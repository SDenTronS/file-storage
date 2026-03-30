package dev.dentron.filestorage.application.service;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.PresignedUrl;
import dev.dentron.filestorage.application.port.in.AbortUploadUseCase;
import dev.dentron.filestorage.application.port.in.CompleteUploadUseCase;
import dev.dentron.filestorage.application.port.in.CreateUploadUseCase;
import dev.dentron.filestorage.application.port.in.DeleteFileUseCase;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort;
import dev.dentron.filestorage.application.port.out.UploadSessionRepository;
import dev.dentron.filestorage.common.util.ExceptionUtils;
import dev.dentron.filestorage.domain.FileObject;
import dev.dentron.filestorage.domain.UploadSession;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor

@Slf4j
@Service
public class FileService implements AbortUploadUseCase, CompleteUploadUseCase, CreateUploadUseCase, DeleteFileUseCase {
    private final UploadSessionRepository sessionRepository;
    private final PersistenceService persistenceService;
    private final ObjectStoragePort storage;
    private final FileAccessService fileAccessService;
    private final DurationProperties properties;
    private final ExecutorService executor;

    @Override
    public CompletableFuture<CreateUploadResult> createMultipartUploadSessionAsync(NamespaceContext ns, CreateUploadRequest request) {
        UUID fileId = UuidCreator.getTimeBased();
        UUID sessionId = UuidCreator.getTimeBased();

        Instant expiresAt = Instant.now().plus(properties.multipart().sessionTtl());
        String objectKey = objectKey(ns, request.prefix(), fileId);
        String bucket = storage.bucket();

        var multipartUploadRequest = new ObjectStoragePort.CreateMultipartUploadRequest(
                bucket,
                storage.region(),
                objectKey
        );

        FileObject file = new FileObject(fileId, ns.serviceId(), objectKey, request.originalFileName(), bucket);

        return storage
                .createMultipartUploadAsync(multipartUploadRequest)
                .orTimeout(properties.multipart().create().toMillis(), TimeUnit.MILLISECONDS)
                .thenApplyAsync(multipartUpload -> {
                    String multipartUploadId = multipartUpload.uploadId();

                    UploadSession session = new UploadSession(
                            sessionId,
                            multipartUploadId,
                            file.getId(),
                            expiresAt,
                            request.expectedContentType()
                    );

                    UUID persistedSessionId = persistenceService.persistSession(file, session);

                    return new CreateUploadResult(
                            persistedSessionId,
                            multipartUploadId,
                            file.getId(),
                            expiresAt);
                }, executor)
                .whenComplete((r, t) -> {
                    if (t != null) {
                        //TODO аборнуть multipart?
                    }
                });
    }

    @Override
    public CompletableFuture<DirectUploadResult> uploadFile(NamespaceContext ns, DirectUploadRequest request) {
        UUID fileId = UuidCreator.getTimeBased();
        String bucket = storage.bucket();
        String objectKey = objectKey(ns, request.prefix(), fileId);
        final InputStream inputStream;
        try {
            inputStream = request.inputStreamSupplier().get();
        } catch (IOException e) {
            return CompletableFuture.failedFuture(new UncheckedIOException("Failed to open upload input stream.", e));
        }

        FileObject file = new FileObject(fileId, ns.serviceId(), objectKey, request.originalFileName(), bucket);

        var putRequest = new ObjectStoragePort.PutObjectRequest(
                bucket,
                objectKey,
                inputStream,
                request.sizeBytes(),
                request.expectedContentType()
        );

        final CompletableFuture<ObjectStoragePort.PutObjectResult> uploadFuture;
        try {
            uploadFuture = storage.putObjectAsync(putRequest);
        } catch (Throwable t) {
            ExceptionUtils.closeQuietly(inputStream, t);
            return CompletableFuture.failedFuture(t);
        }

        //TODO тут тоже про базу

        return uploadFuture
                .thenApplyAsync(putObjectResult -> {
                    persistenceService.persistUploaded(file, putObjectResult.etag());

                    return new DirectUploadResult(file.getId(), putObjectResult.etag());
                }, executor)
                .whenComplete((r, t) -> ExceptionUtils.closeQuietly(inputStream, t));
    }

    @Override
    public PresignedUrl presignMultipartPut(NamespaceContext ns, PresignedMultipartPutRequest request) {
        UploadSession session = sessionRepository.findByMultipartUploadId(request.multipartUploadId())
                .orElseThrow(() -> new EntityNotFoundException("Session not found with multipart upload id: " + request.multipartUploadId()));

        return presignPutQuery(ns, session, Map.of(
                "uploadId", request.multipartUploadId(),
                "partNumber", String.valueOf(request.partNumber())
        ));
    }

    @Override
    public PresignedUrl presignPut(NamespaceContext ns, PresignedPutRequest request) {
        UploadSession session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new EntityNotFoundException("Session not found with id: " + request.sessionId()));

        return presignPutQuery(ns, session, Map.of());
    }

    private PresignedUrl presignPutQuery(NamespaceContext ns, UploadSession session, Map<String, String> query) {
        Instant now = Instant.now();
        session.ensureAvailable(now);

        FileObject file = fileAccessService.getByIdOwnedBy(session.getFileId(), ns);

        Duration ttl = properties.presign().putTtl();

        return new PresignedUrl(storage.presign(
                new ObjectStoragePort.PresignedRequest(
                        file.getBucket(),
                        file.getObjectKey(),
                        ObjectStoragePort.PresignMethod.PUT,
                        query,
                        ttl
                )
        ), now.plus(ttl));
    }


    @Override
    public CompletableFuture<CompleteUploadResult> completeMultipartUpload(NamespaceContext ns, CompleteUploadRequest request) {
        UploadSession session = persistenceService.persistCompleting(request.multipartUploadId());
        FileObject file = fileAccessService.getByIdOwnedBy(session.getFileId(), ns);

        var completeRequest = new ObjectStoragePort.CompleteMultipartRequest(
                storage.bucket(),
                file.getObjectKey(),
                request.multipartUploadId(),
                request.parts()
        );

        //TODO подумать над тем что будет если база наебнется

        return storage
                .completeMultipartUploadAsync(completeRequest)
                .thenApplyAsync((etag) -> {
                    if (etag == null) {
                        return new CompleteUploadResult(file.getId(), null);
                    }

                    persistenceService.persistCompleted(file.getId(), session.getId(), etag);
                    return new CompleteUploadResult(file.getId(), etag);
                }, executor);
    }

    @Override
    public void abortUpload(NamespaceContext ns, AbortUploadRequest request) {
        UploadSession session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new EntityNotFoundException("Session not found with id: " + request.sessionId()));

        fileAccessService.getByIdOwnedBy(session.getFileId(), ns);
        persistenceService.persistAborted(session.getId(), Instant.now());
    }

    @Override
    public void deleteFile(NamespaceContext ns, DeleteFileRequest request) {
        FileObject file = fileAccessService.getByIdOwnedBy(request.fileId(), ns);
        Instant now = Instant.now();
        persistenceService.persistDeleted(file.getId(), now);
    }

    private String objectKey(NamespaceContext ns, String prefix, UUID fileId) {
        return ns.root() + prefix + "/" + fileId;
    }
}
