package dev.dentron.filestorage.api;

import dev.dentron.filestorage.application.port.out.FileObjectRepository;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort;
import dev.dentron.filestorage.application.port.out.UploadSessionRepository;
import dev.dentron.filestorage.application.port.out.outbox.OutboxPort;
import dev.dentron.filestorage.domain.FileObject;
import dev.dentron.filestorage.domain.UploadSession;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.when;

abstract class AbstractFileStorageITSupport {
    @MockitoBean
    protected ObjectStoragePort storagePort;

    @MockitoBean
    protected OutboxPort outboxPort;

    @Autowired
    protected FileObjectRepository fileRepository;

    @Autowired
    protected UploadSessionRepository sessionRepository;

    @BeforeEach
    void setUpStorageMocks() {
        clearInvocations(outboxPort);
        when(storagePort.completeMultipartUploadAsync(any()))
                .thenReturn(CompletableFuture.completedFuture("e-tag"));
        when(storagePort.abortMultipartUploadAsync(any()))
                .thenReturn(CompletableFuture.completedFuture(ObjectStoragePort.AbortResult.ABORTED));

        var multipartCreateAnswer = new ObjectStoragePort.MultipartUpload("uploadId");
        when(storagePort.createMultipartUploadAsync(any()))
                .thenReturn(CompletableFuture.completedFuture(multipartCreateAnswer));
    }

    protected UploadFixture createSessionAndFile(String ownerService) {
        return createSessionAndFile(ownerService, Instant.now().plusSeconds(3600));
    }

    protected UploadFixture createSessionAndFile(String ownerService, Instant expiresAt) {
        UUID fileId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String multipartUploadId = "upload-" + UUID.randomUUID();

        FileObject file = new FileObject(
                fileId,
                ownerService,
                "uploads/" + fileId,
                "test.bin",
                "bucket-main"
        );
        fileRepository.save(file);

        UploadSession session = new UploadSession(
                sessionId,
                multipartUploadId,
                fileId,
                expiresAt,
                MediaType.APPLICATION_OCTET_STREAM_VALUE
        );
        sessionRepository.save(session);

        return new UploadFixture(fileId, sessionId, multipartUploadId);
    }

    protected record UploadFixture(UUID fileId, UUID sessionId, String multipartUploadId) {}
}
