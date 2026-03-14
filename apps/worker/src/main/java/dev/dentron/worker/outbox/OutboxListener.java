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
import dev.dentron.filestorage.domain.FileObject;
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
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Slf4j
@RequiredArgsConstructor
@Component
public class OutboxListener {
    private final ObjectStoragePort storage;
    private final PersistenceService persistenceService;
    private final UploadSessionRepository sessionRepository;
    private final FileObjectRepository fileRepository;
    private final OutboxFailMarker failMarker;
    private final Tika tika;
    private final ObjectMapper mapper;
    private final ExecutorService executor;

    @KafkaListener(
            groupId = "${app.kafka.listener.group-id}",
            topics = "${app.kafka.topics.file-uploaded.name}",
            containerFactory = "outbox-container-factory",
            errorHandler = "log-listener-error-handler"
    )
    public CompletableFuture<Void> listenUploaded(@Payload OutboxMessage message, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        log.debug("Received outbox message {}", message);
        if (message.eventType() != OutboxEventType.FILE_UPLOADED) {
            throw new IllegalStateException("Expected event type FILE_UPLOADED but got " + message.eventType());
        }

        return CompletableFuture.runAsync(() -> {
            handleFileUploaded(message, key);
        }, executor);
    }

    @KafkaListener(
            groupId = "${app.kafka.listener.group-id}",
            topics = "${app.kafka.topics.file-deleted.name}",
            containerFactory = "outbox-container-factory",
            errorHandler = "log-listener-error-handler"
    )
    public CompletableFuture<Void> listenDeleted(@Payload OutboxMessage message, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        log.debug("Received outbox message {}", message);
        if (message.eventType() != OutboxEventType.FILE_DELETED) {
            throw new IllegalStateException("Expected event type FILE_DELETED but got " + message.eventType());
        }

        return handleFileDeleted(message, key)
                .whenComplete((v, e) -> {
                    if (e != null) {
                        log.error("Error while handling outbox message {}", message, e);
                    }
                });
    }

    // TODO batch listener
    @KafkaListener(
            groupId = "${app.kafka.listener.group-id}",
            topics = {
                    "${app.kafka.topics.file-uploaded.dlt-name}",
                    "${app.kafka.topics.file-deleted.dlt-name}"
            },
            containerFactory = "outbox-container-factory",
            errorHandler = "log-listener-error-handler"
    )
    public void listenDlt(@Payload List<OutboxMessage> messages) {
        List<UUID> ids = messages.stream().map(OutboxMessage::eventId).toList();
        failMarker.markFailed(ids);
    }

    //TODO reduce blocking ops
    private void handleFileUploaded(OutboxMessage message, String key) {
        FileUploadedPayload payload = mapper.readValue(message.payloadJson(), FileUploadedPayload.class);

        FileView fileView = fileRepository.findViewById(payload.fileId()).orElseThrow(() -> {
            log.error("File not found with id {}; outbox {}", payload.fileId(), message);
            return new EntityNotFoundException("File not found with id: " + payload.fileId());
        });

        if (fileView.getStatus() == FileObject.Status.DELETED) {
            log.debug("Skip FILE_UPLOADED for fileId={}, file already transitioned to incompatible state", fileView.getId());
            return;
        }

        String expectedContentType = sessionRepository
                .findByFileId(payload.fileId())
                .map(UploadSession::getExpectedContentType)
                .orElse(null);

        var objRequest = new ObjectStoragePort.GetObjectRequest(
                fileView.getBucket(),
                fileView.getObjectKey(),
                0,
                DataSize.ofKilobytes(64).toBytes()
        );

        MediaType detected = detectMediaType(fileView, objRequest);
        if (detected == null) {
            persistenceService.persistRejected(fileView.getId());
            return;
        }

        if (!matchesExpected(detected, expectedContentType)) {
            log.warn("Detected content type {} for file {} does not match expected {}", detected, fileView.getObjectKey(), expectedContentType);
            persistenceService.persistRejected(fileView.getId());
            throw new IllegalStateException("Detected content type does not match expected");
        }

        var metaRequest = new ObjectStoragePort.GetObjectMetadataRequest(fileView.getBucket(), fileView.getObjectKey());
        ObjectMetadata fileMeta = storage.getObjectMetadata(metaRequest).join();

        persistenceService.persistReady(fileView.getId(), detected.getBaseType().toString(), fileMeta.size());
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
            return detect(obj, fileView.getOriginalName());
        } catch (java.util.concurrent.CompletionException e) {
            Throwable cause = ExceptionUtils.unwrap(e);
            log.warn("Failed to get object for detection; file {}", fileView.getObjectKey(), cause);
            return null;
        } catch (Exception e) {
            log.warn("Failed to detect content type for file {}", fileView.getObjectKey(), e);
            return null;
        }
    }

    private MediaType detect(StorageObject object,
                             String fileName) throws IOException {
        Metadata metadata = new Metadata();

        if (fileName != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }

        InputStream input = object.in();
        if (!input.markSupported()) {
            input = new BufferedInputStream(input);
        }

        input.mark(64 * 1024);
        return tika.getDetector().detect(input, metadata);
    }

    private CompletableFuture<Void> handleFileDeleted(OutboxMessage message, String key) {
        FileDeletedPayload payload = mapper.readValue(message.payloadJson(), FileDeletedPayload.class);
        Objects.requireNonNull(payload.bucket(), "bucket cannot be null");
        Objects.requireNonNull(payload.objectKey(), "objectKey cannot be null");

        var abortRequest = new ObjectStoragePort.AbortMultipartUploadRequest(payload.bucket(), payload.objectKey(), payload.uploadId());
        var abortFuture = storage.abortMultipartUploadAsync(abortRequest).whenComplete((r, t) -> {
            if (t != null) {
                log.warn("Abort multipart upload failed for file {}", payload.objectKey(), t);
            }
        });

        var deleteRequest = new ObjectStoragePort.DeleteObjectRequest(payload.bucket(), payload.objectKey());
        var deleteFuture = storage.deleteObjectAsync(deleteRequest).whenComplete((r, t) -> {
            if (t != null) {
                log.warn("Delete multipart upload failed for file {}", payload.objectKey(), t);
            }
        });

        return CompletableFuture.allOf(abortFuture, deleteFuture);
    }
}
