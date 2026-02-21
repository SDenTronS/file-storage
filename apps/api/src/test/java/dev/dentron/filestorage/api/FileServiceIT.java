package dev.dentron.filestorage.api;

import dev.dentron.filestorage.api.security.jwt.JwtTokenVerifier;
import dev.dentron.filestorage.application.outbox.OutboxEventType;
import dev.dentron.filestorage.application.outbox.OutboxMessage;
import dev.dentron.filestorage.application.port.FilePart;
import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.in.CompleteUploadUseCase;
import dev.dentron.filestorage.application.port.in.CreateUploadUseCase;
import dev.dentron.filestorage.application.port.in.DeleteFileUseCase;
import dev.dentron.filestorage.application.port.out.FileObjectRepository;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort;
import dev.dentron.filestorage.application.port.out.UploadSessionRepository;
import dev.dentron.filestorage.application.port.out.outbox.OutboxPort;
import dev.dentron.filestorage.application.service.FileService;
import dev.dentron.filestorage.domain.FileObject;
import dev.dentron.filestorage.domain.UploadSession;
import dev.dentron.filestorage.domain.exception.UploadSessionException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@SpringBootTest(classes = ApiApplication.class, properties = {
        "app.minio.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
public class FileServiceIT {

    @MockitoBean private ObjectStoragePort storagePort;
    @MockitoBean private OutboxPort outboxPort;

    /** Disable real JWT verification for tests. */
    @MockitoBean private JwtTokenVerifier tokenVerifier;

    @Autowired
    private FileService fileService;

    @Autowired
    private FileObjectRepository fileRepository;

    @Autowired
    private UploadSessionRepository sessionRepository;

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgresContainer = new PostgreSQLContainer(
            DockerImageName.parse( "postgres:17.5")
    );

    @BeforeEach
    void setUp() {
        clearInvocations(outboxPort);
        when(storagePort.completeMultipartUploadAsync(any())).thenReturn(CompletableFuture.completedFuture("e-tag"));
        when(storagePort.abortMultipartUploadAsync(any())).thenReturn(CompletableFuture.completedFuture(ObjectStoragePort.AbortResult.ABORTED));

        var multipartCreateAnswer = new ObjectStoragePort.MultipartUpload("uploadId");
        when(storagePort.createMultipartUploadAsync(any())).thenReturn(CompletableFuture.completedFuture(multipartCreateAnswer));
    }

    @Test
    void testCompleteAfterDeleted() {
        NamespaceContext ns = new NamespaceContext("svc-a");
        UploadFixture fixture = createSessionAndFile("svc-a");

        fileService.deleteFile(ns, new DeleteFileUseCase.DeleteFileRequest(fixture.fileId()));

        FileObject file = fileRepository.findById(fixture.fileId()).orElseThrow();
        assertThat(file.getStatus()).isEqualTo(FileObject.Status.DELETED);
        assertThat(file.getOwner()).isEqualTo(ns.serviceId());

        UploadSession session = sessionRepository.findById(fixture.sessionId()).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(UploadSession.Status.ABORTED);

        verify(outboxPort).enqueueOutboxEvent(any());

        clearInvocations(outboxPort, storagePort);

        var request = new CompleteUploadUseCase.CompleteUploadRequest(
                fixture.multipartUploadId(),
                List.of(new FilePart("etag-1", 1))
        );

        assertThatThrownBy(() -> fileService.completeMultipartUpload(ns, request).join())
                .isInstanceOf(EntityNotFoundException.class);

        verify(storagePort, never()).completeMultipartUploadAsync(any());
        verify(outboxPort, never()).enqueueOutboxEvent(any());
    }

    @Test
    void testCompleteMultipartUploadMarksUploadedAndEnqueuesOutbox() {
        NamespaceContext ns = new NamespaceContext("svc-a");
        UploadFixture fixture = createSessionAndFile("svc-a");

        var request = new CompleteUploadUseCase.CompleteUploadRequest(
                fixture.multipartUploadId(),
                List.of(new FilePart("etag-1", 1))
        );

        var result = fileService.completeMultipartUpload(ns, request).join();

        assertThat(result.fileId()).isEqualTo(fixture.fileId());
        assertThat(result.etag()).isEqualTo("e-tag");

        FileObject file = fileRepository.findById(fixture.fileId()).orElseThrow();
        assertThat(file.getStatus()).isEqualTo(FileObject.Status.UPLOADED);
        assertThat(file.getEtag()).isEqualTo("e-tag");

        UploadSession session = sessionRepository.findById(fixture.sessionId()).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(UploadSession.Status.COMPLETED);

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxPort).enqueueOutboxEvent(captor.capture());
        OutboxMessage message = captor.getValue();
        assertThat(message.eventType()).isEqualTo(OutboxEventType.FILE_UPLOADED);
        assertThat(message.aggregateId()).isEqualTo(fixture.fileId().toString());
    }

    @Test
    void testDeleteFileAbortsSessionAndEnqueuesOutbox() {
        NamespaceContext ns = new NamespaceContext("svc-a");
        UploadFixture fixture = createSessionAndFile("svc-a");

        fileService.deleteFile(ns, new DeleteFileUseCase.DeleteFileRequest(fixture.fileId()));

        FileObject file = fileRepository.findById(fixture.fileId()).orElseThrow();
        assertThat(file.getStatus()).isEqualTo(FileObject.Status.DELETED);
        assertThat(file.getDeletedAt()).isNotNull();

        UploadSession session = sessionRepository.findById(fixture.sessionId()).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(UploadSession.Status.ABORTED);

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxPort).enqueueOutboxEvent(captor.capture());
        OutboxMessage message = captor.getValue();
        assertThat(message.eventType()).isEqualTo(OutboxEventType.FILE_DELETED);
        assertThat(message.aggregateId()).isEqualTo(fixture.fileId().toString());
        assertThat(message.payloadJson()).contains("bucket-main", "uploads/" + fixture.fileId());
    }

    @Test
    void testDeleteFileForbiddenForWrongOwner() {
        UploadFixture fixture = createSessionAndFile("svc-a");
        NamespaceContext otherNs = new NamespaceContext("svc-b");

        assertThatThrownBy(() -> fileService.deleteFile(otherNs, new DeleteFileUseCase.DeleteFileRequest(fixture.fileId())))
                .isInstanceOf(AccessDeniedException.class);

        FileObject file = fileRepository.findById(fixture.fileId()).orElseThrow();
        assertThat(file.getStatus()).isEqualTo(FileObject.Status.UPLOADING);

        verify(outboxPort, never()).enqueueOutboxEvent(any());
    }

    @Test
    void testPresignPutThrowsWhenSessionExpired() {
        NamespaceContext ns = new NamespaceContext("svc-a");
        UploadFixture fixture = createSessionAndFile("svc-a", Instant.now().minusSeconds(30), false);

        var request = new CreateUploadUseCase.PresignedPutRequest(fixture.sessionId());

        assertThatThrownBy(() -> fileService.presignPut(ns, request))
                .isInstanceOfSatisfying(UploadSessionException.class, ex ->
                        assertThat(ex.reason()).isEqualTo(UploadSessionException.Reason.EXPIRED));
    }

    private UploadFixture createSessionAndFile(String ownerService) {
        return createSessionAndFile(ownerService, Instant.now().plusSeconds(3600), true);
    }

    private UploadFixture createSessionAndFile(String ownerService, Instant expiresAt, boolean markPartsUploaded) {
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
        if (markPartsUploaded) {
            sessionRepository.tryMarkPartsUploaded(sessionId);
        }

        return new UploadFixture(fileId, sessionId, multipartUploadId);
    }

    private record UploadFixture(UUID fileId, UUID sessionId, String multipartUploadId) {}

}
