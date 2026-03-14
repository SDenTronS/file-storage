package dev.dentron.filestorage.application.service;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dentron.filestorage.application.exception.ConflictException;
import dev.dentron.filestorage.application.port.out.FileObjectRepository;
import dev.dentron.filestorage.application.port.out.UploadSessionRepository;
import dev.dentron.filestorage.application.outbox.AggregateType;
import dev.dentron.filestorage.application.outbox.OutboxEventType;
import dev.dentron.filestorage.application.outbox.OutboxMessage;
import dev.dentron.filestorage.application.port.out.outbox.OutboxPort;
import dev.dentron.filestorage.application.outbox.payload.FileDeletedPayload;
import dev.dentron.filestorage.application.outbox.payload.FileUploadedPayload;
import dev.dentron.filestorage.domain.FileObject;
import dev.dentron.filestorage.domain.UploadSession;
import dev.dentron.filestorage.domain.exception.UploadSessionException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor

@Service
public class PersistenceService {

    private final FileObjectRepository fileRepository;
    private final UploadSessionRepository uploadSessionRepository;
    private final ObjectMapper objectMapper;
    private final OutboxPort outbox;

    @Transactional
    public UUID persistSession(FileObject file, UploadSession session) {
        fileRepository.save(file);
        uploadSessionRepository.save(session);
        return session.getId();
    }

    @Transactional
    public void persistUploaded(FileObject file, String etag) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(etag, "etag");

        file.markUploaded();
        file.setEtag(etag);
        fileRepository.save(file);

        var payload = new FileUploadedPayload(file.getId());
        var outboxMessage = box(OutboxEventType.FILE_UPLOADED, AggregateType.FILE, file.getId().toString(), payload);
        outbox.enqueueOutboxEvent(outboxMessage);
    }

    @Transactional
    public void persistReady(UUID fileId, String contentType, Long size) {
        fileRepository.tryMarkReady(fileId, contentType, size)
                .orElseThrow(() -> new RuntimeException("Failed to mark file as ready"));
    }


    private RuntimeException conflictOrNotFound(UUID sessionId) {
        return new EntityNotFoundException("Upload session not found with id: " + sessionId);
    }

    private RuntimeException conflictOrNotFound(String sessionId) {
        return new EntityNotFoundException("Upload session not found with id: " + sessionId);
    }

    private UUID resolveSessionIdByUploadId(String multipartUploadId) {
        return uploadSessionRepository.findIdByMultipartUploadId(multipartUploadId)
                .orElseThrow(() -> conflictOrNotFound(multipartUploadId));
    }


    @Transactional
    public UploadSession persistCompleting(String multipartUploadId) {
        UUID sessionId = resolveSessionIdByUploadId(multipartUploadId);
        return uploadSessionRepository.tryMarkCompleting(sessionId).orElseThrow(() -> conflictOrNotFound(sessionId));
    }


    @Transactional
    public void persistCompleted(UUID fileId, UUID sessionId, String etag) {
        FileObject file = fileRepository.findForUpdateById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("File not found with id: " + fileId));

        uploadSessionRepository.tryMarkCompleted(sessionId).orElseThrow(() -> conflictOrNotFound(sessionId));

        if (file.getStatus() == FileObject.Status.DELETED) {
            throw new IllegalStateException("File already deleted for session with id");
        }

        if (file.getStatus() == FileObject.Status.UPLOADED)
            return;

        file.markUploaded();
        file.setEtag(etag);

        fileRepository.save(file);

        var payload = new FileUploadedPayload(fileId);
        var outboxMessage = box(OutboxEventType.FILE_UPLOADED, AggregateType.FILE, fileId.toString(), payload);
        outbox.enqueueOutboxEvent(outboxMessage);
    }

    @Transactional
    public void persistDeleted(UUID fileId, Instant now) {
        Objects.requireNonNull(fileId, "fileId cannot be null");

        FileObject file = fileRepository.findForUpdateById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("File not found with id: " + fileId));

        if (file.getStatus() == FileObject.Status.UPLOADING) {
            throw new ConflictException(
                    "UPLOAD_IN_PROGRESS",
                    "Upload is still in progress. Use DELETE /v1/uploads/{sessionId} to abort it."
            );
        }

        if (file.getStatus() == FileObject.Status.DELETED)
            return;

        fileRepository.tryMarkDeleted(fileId, now);

        var payload = new FileDeletedPayload(file.getBucket(), null, file.getObjectKey());
        var outboxMessage = box(OutboxEventType.FILE_DELETED, AggregateType.FILE, fileId.toString(), payload);
        outbox.enqueueOutboxEvent(outboxMessage);
    }

    @Transactional
    public void persistAborted(UUID sessionId, Instant now) {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");


        var sessionView = uploadSessionRepository.tryMarkAborted(sessionId)
                .orElseThrow(() -> new UploadSessionException(
                        UploadSessionException.Reason.INVALID_STATE,
                        "Upload session can no longer be aborted"
                ));


        FileObject file = fileRepository.findForUpdateById(sessionView.getFileId())
                .orElseThrow(() -> new EntityNotFoundException("File not found with id: " + sessionView.getFileId()));

        if (file.getStatus() == FileObject.Status.DELETED)
            return;

        fileRepository.tryMarkDeleted(file.getId(), now)
                .orElseThrow(() -> new IllegalStateException("Failed to delete file for aborted upload session"));

        var payload = new FileDeletedPayload(file.getBucket(), sessionView.getMultipartUploadId(), file.getObjectKey());
        var outboxMessage = box(OutboxEventType.FILE_DELETED, AggregateType.FILE, file.getId().toString(), payload);
        outbox.enqueueOutboxEvent(outboxMessage);
    }

    @Transactional
    public void persistRejected(UUID fileId) {
        Objects.requireNonNull(fileId, "fileId cannot be null");

        FileObject file = fileRepository.findForUpdateById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("File not found with id: " + fileId));

        if (file.getStatus() == FileObject.Status.REJECTED)
            return;

        fileRepository.tryMarkRejected(fileId);
    }

    private OutboxMessage box(OutboxEventType type, AggregateType agType, String aggregateId, Object payload) {
        return new OutboxMessage(
                UuidCreator.getTimeBased(),
                type,
                objectMapper.writeValueAsString(payload),
                agType,
                aggregateId,
                Instant.now()
        );
    }
}
