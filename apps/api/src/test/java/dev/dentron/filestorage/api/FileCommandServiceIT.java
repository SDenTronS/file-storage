package dev.dentron.filestorage.api;

import dev.dentron.filestorage.application.exception.ConflictException;
import dev.dentron.filestorage.application.outbox.OutboxEventType;
import dev.dentron.filestorage.application.outbox.OutboxMessage;
import dev.dentron.filestorage.application.port.FilePart;
import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.in.AbortUploadUseCase;
import dev.dentron.filestorage.application.port.in.CompleteUploadUseCase;
import dev.dentron.filestorage.application.port.in.CreateUploadUseCase;
import dev.dentron.filestorage.application.port.in.DeleteFileUseCase;
import dev.dentron.filestorage.application.service.FileService;
import dev.dentron.filestorage.domain.FileObject;
import dev.dentron.filestorage.domain.UploadSession;
import dev.dentron.filestorage.domain.exception.UploadSessionException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.access.AccessDeniedException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = ApiApplication.class, properties = {
        "app.minio.enabled=false",
        "app.security.enabled=false",
        "app.kafka.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class FileCommandServiceIT extends AbstractFileStorageITSupport {
    @Autowired
    private FileService fileService;

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgresContainer = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.5")
    );

    @Test
    void completeAfterDeletedStopsWithoutStorageCall() {
        NamespaceContext ns = new NamespaceContext("svc-a");
        UploadFixture fixture = createSessionAndFile("svc-a");

        fileService.abortUpload(ns, new AbortUploadUseCase.AbortUploadRequest(fixture.sessionId()));

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
    void completeMultipartUploadMarksUploadedAndEnqueuesOutbox() {
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
        assertThat(captor.getValue().eventType()).isEqualTo(OutboxEventType.FILE_UPLOADED);
        assertThat(captor.getValue().aggregateId()).isEqualTo(fixture.fileId().toString());
    }

    @Test
    void abortUploadMarksFileDeletedAndEnqueuesOutbox() {
        NamespaceContext ns = new NamespaceContext("svc-a");
        UploadFixture fixture = createSessionAndFile("svc-a");

        fileService.abortUpload(ns, new AbortUploadUseCase.AbortUploadRequest(fixture.sessionId()));

        FileObject file = fileRepository.findById(fixture.fileId()).orElseThrow();
        assertThat(file.getStatus()).isEqualTo(FileObject.Status.DELETED);
        assertThat(file.getDeletedAt()).isNotNull();

        UploadSession session = sessionRepository.findById(fixture.sessionId()).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(UploadSession.Status.ABORTED);

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxPort).enqueueOutboxEvent(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(OutboxEventType.FILE_DELETED);
        assertThat(captor.getValue().aggregateId()).isEqualTo(fixture.fileId().toString());
        assertThat(captor.getValue().payloadJson()).contains("bucket-main", "uploads/" + fixture.fileId());
    }

    @Test
    void abortUploadRejectsWrongOwner() {
        UploadFixture fixture = createSessionAndFile("svc-a");
        NamespaceContext otherNs = new NamespaceContext("svc-b");

        assertThatThrownBy(() -> fileService.abortUpload(otherNs, new AbortUploadUseCase.AbortUploadRequest(fixture.sessionId())))
                .isInstanceOf(AccessDeniedException.class);

        FileObject file = fileRepository.findById(fixture.fileId()).orElseThrow();
        assertThat(file.getStatus()).isEqualTo(FileObject.Status.UPLOADING);
        verify(outboxPort, never()).enqueueOutboxEvent(any());
    }

    @Test
    void presignPutThrowsWhenSessionExpired() {
        NamespaceContext ns = new NamespaceContext("svc-a");
        UploadFixture fixture = createSessionAndFile("svc-a", Instant.now().minusSeconds(30));

        var request = new CreateUploadUseCase.PresignedPutRequest(fixture.sessionId());

        assertThatThrownBy(() -> fileService.presignPut(ns, request))
                .isInstanceOfSatisfying(UploadSessionException.class, ex ->
                        assertThat(ex.reason()).isEqualTo(UploadSessionException.Reason.EXPIRED));
    }

    @Test
    void deleteFileRejectsUploadingFile() {
        NamespaceContext ns = new NamespaceContext("svc-a");
        UploadFixture fixture = createSessionAndFile("svc-a");

        assertThatThrownBy(() -> fileService.deleteFile(ns, new DeleteFileUseCase.DeleteFileRequest(fixture.fileId())))
                .isInstanceOfSatisfying(ConflictException.class, ex ->
                        assertThat(ex.code()).isEqualTo("UPLOAD_IN_PROGRESS"));

        FileObject file = fileRepository.findById(fixture.fileId()).orElseThrow();
        assertThat(file.getStatus()).isEqualTo(FileObject.Status.UPLOADING);

        UploadSession session = sessionRepository.findById(fixture.sessionId()).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(UploadSession.Status.CREATED);
        verify(outboxPort, never()).enqueueOutboxEvent(any());
    }

    @Test
    void deleteFileMarksCompletedFileDeleted() {
        NamespaceContext ns = new NamespaceContext("svc-a");
        UploadFixture fixture = createSessionAndFile("svc-a");

        fileService.completeMultipartUpload(ns, new CompleteUploadUseCase.CompleteUploadRequest(
                fixture.multipartUploadId(),
                List.of(new FilePart("etag-1", 1))
        )).join();
        clearInvocations(outboxPort);

        fileService.deleteFile(ns, new DeleteFileUseCase.DeleteFileRequest(fixture.fileId()));

        FileObject file = fileRepository.findById(fixture.fileId()).orElseThrow();
        assertThat(file.getStatus()).isEqualTo(FileObject.Status.DELETED);
        assertThat(file.getDeletedAt()).isNotNull();

        UploadSession session = sessionRepository.findById(fixture.sessionId()).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(UploadSession.Status.COMPLETED);

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxPort).enqueueOutboxEvent(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(OutboxEventType.FILE_DELETED);
    }
}
