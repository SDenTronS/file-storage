package dev.dentron.worker.outbox;

import dev.dentron.filestorage.application.service.PersistenceService;
import dev.dentron.filestorage.application.port.out.FileObjectRepository;
import dev.dentron.filestorage.application.port.out.FileObjectRepository.FileView;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort.ObjectMetadata;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort.StorageObject;
import dev.dentron.filestorage.application.port.out.UploadSessionRepository;
import dev.dentron.filestorage.application.outbox.OutboxEventType;
import dev.dentron.filestorage.application.port.out.outbox.OutboxFailMarker;
import dev.dentron.filestorage.application.outbox.OutboxMessage;
import dev.dentron.filestorage.application.outbox.payload.FileDeletedPayload;
import dev.dentron.filestorage.application.outbox.payload.FileUploadedPayload;
import dev.dentron.filestorage.common.util.ExceptionUtils;
import dev.dentron.filestorage.domain.UploadSession;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class OutboxListener {
    private final Map<OutboxEventType, EventHandler> EVENT_HANDLERS = Map.of(
            OutboxEventType.FILE_UPLOADED, this::handleFileUploaded,
            OutboxEventType.FILE_DELETED, this::handleFileDeleted
    );

    private final ObjectStoragePort storage;
    private final PersistenceService persistenceService;
    private final UploadSessionRepository sessionRepository;
    private final FileObjectRepository fileRepository;
    private final OutboxFailMarker failMarker;
    private final Tika tika;
    private final ObjectMapper mapper;

    @KafkaListener(topics = "file-storage", containerFactory = "outbox-container-factory")
    public void listen(@Payload OutboxMessage message, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        OutboxEventType type = message.eventType();
        EventHandler handler = EVENT_HANDLERS.get(type);

        if (handler == null) {
            throw new IllegalStateException("Unsupported event type: " + type);
        }

        handler.handle(message, key);
    }

    @KafkaListener(topics = "file-storage-dlt")
    public void listenDlt(@Payload List<OutboxMessage> messages) {
        List<UUID> ids = messages.stream().map(OutboxMessage::eventId).toList();
        failMarker.markFailed(ids);
    }

    //TODO может добавить асинхронность
    private void handleFileUploaded(OutboxMessage message, String key) {
        FileUploadedPayload payload = mapper.readValue(message.payloadJson(), FileUploadedPayload.class);

        FileView fileView = fileRepository.findViewById(payload.fileId()).orElseThrow(() -> {
            log.error("File not found with id {}; outbox {}", payload.fileId(), message);
            return new EntityNotFoundException("File not found with id: " + payload.fileId());
        });

        String expectedContentType = sessionRepository
                .findByFileId(payload.fileId())
                .map(UploadSession::getExpectedContentType)
                .orElse(null);

        var objRequest = new ObjectStoragePort.GetObjectRequest(
                fileView.bucket(),
                fileView.objectKey(),
                0,
                DataSize.ofKilobytes(64).toBytes()
        );

        MediaType detected = detectMediaType(fileView, objRequest);
        if (detected == null) {
            persistenceService.persistRejected(fileView.id());
            return;
        }

        if (!matchesExpected(detected, expectedContentType)) {
            log.warn("Detected content type {} for file {} does not match expected {}", detected, fileView.objectKey(), expectedContentType);
            persistenceService.persistRejected(fileView.id());
            throw new IllegalStateException("Detected content type does not match expected");
        }

        var metaRequest = new ObjectStoragePort.GetObjectMetadataRequest(fileView.bucket(), fileView.objectKey());
        ObjectMetadata fileMeta = storage.getObjectMetadata(metaRequest).join();

        persistenceService.persistReady(fileView.id(), detected.getBaseType().toString(), fileMeta.size());
    }

    private boolean matchesExpected(MediaType detected, String expectedContentType) {
        if (expectedContentType == null || expectedContentType.isBlank()) {
            return true;
        }

        final MediaType expectedBase;
        expectedBase = MediaType.parse(expectedContentType);

        return detected.equals(expectedBase);
    }

    private MediaType detectMediaType(FileView fileView, ObjectStoragePort.GetObjectRequest objRequest) {
        try (StorageObject obj = storage.getObject(objRequest).join()) {
            return detect(obj, fileView.originalName());
        } catch (java.util.concurrent.CompletionException e) {
            Throwable cause = ExceptionUtils.unwrap(e);
            log.warn("Failed to get object for detection; file {}", fileView.objectKey(), cause);
            return null;
        } catch (Exception e) {
            log.warn("Failed to detect content type for file {}", fileView.objectKey(), e);
            return null;
        }
    }

    private MediaType detect(StorageObject object,
                             String fileName) throws IOException {
        Metadata metadata = new Metadata();

        if (fileName != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }

        return tika.getDetector().detect(object.in(), metadata);
    }

    private void handleFileDeleted(OutboxMessage message, String key) {
        FileDeletedPayload payload = mapper.readValue(message.payloadJson(), FileDeletedPayload.class);
        Objects.requireNonNull(payload.bucket(), "bucket cannot be null");
        Objects.requireNonNull(payload.objectKey(), "objectKey cannot be null");

        var request = new ObjectStoragePort.AbortMultipartUploadRequest(payload.bucket(), payload.objectKey(), payload.uploadId());
        storage.abortMultipartUploadAsync(request).whenComplete((r, t) -> {
            if (t != null) {
                log.warn("Abort multipart upload failed for file {}", payload.objectKey(), t);
            }

        });
    }

    @FunctionalInterface
    private interface EventHandler {
        void handle(OutboxMessage message, String key);
    }
}
