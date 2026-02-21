package dev.dentron.filestorage.application.port.in;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dentron.filestorage.application.port.FilePart;
import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.out.FileObjectRepository;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort;
import dev.dentron.filestorage.application.port.out.UploadSessionRepository;
import dev.dentron.filestorage.application.port.out.outbox.OutboxPort;
import dev.dentron.filestorage.application.outbox.OutboxMessage;
import dev.dentron.filestorage.application.service.DurationProperties;
import dev.dentron.filestorage.application.service.FileService;
import dev.dentron.filestorage.application.service.PersistenceService;
import dev.dentron.filestorage.domain.FileObject;
import dev.dentron.filestorage.domain.UploadSession;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.Id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompleteUploadUseCaseTest {

    @Mock private ObjectStoragePort storage;
    @Mock private PersistenceService persistence;
    @Mock private FileObjectRepository fileRepository;
    @Mock private UploadSessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        clearInvocations(persistence, storage, sessionRepository);
    }

    @Test
    void completeMultipartUpload_persistsEtagAndReturnsResult() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            var ns = new NamespaceContext("svc-a");
            var fileId = UuidCreator.fromString("00000000-0000-0000-0000-000000000001");
            var sessionId = UuidCreator.fromString("00000000-0000-0000-0000-000000000002");
            var uploadId = "upload-1";
            var parts = List.of(new FilePart("etag-1", 1), new FilePart("etag-2", 2));

            var session = mock(UploadSession.class);
            when(session.getId()).thenReturn(sessionId);
            when(session.getFileId()).thenReturn(fileId);

            var file = mock(FileObject.class);
            when(fileRepository.findById(fileId)).thenReturn(Optional.of(file));
            when(file.getObjectKey()).thenReturn("obj-key");
            when(file.getId()).thenReturn(fileId);
            when(file.isOwnedBy(anyString())).thenReturn(true);

            when(persistence.persistCompleting(uploadId)).thenReturn(session);

            when(storage.bucket()).thenReturn("bucket-1");

            var service = new FileService(
                    fileRepository,
                    sessionRepository,
                    persistence,
                    null,
                    storage,
                    null,
                    durations(),
                    executor
            );

            var request = new CompleteUploadUseCase.CompleteUploadRequest(uploadId, parts);
            when(storage.completeMultipartUploadAsync(any())).thenReturn(CompletableFuture.completedFuture("etag-final"));


            var result = service.completeMultipartUpload(ns, request).get(1, TimeUnit.SECONDS);

            assertThat(fileId).isEqualTo(result.fileId());
            assertThat("etag-final").isEqualTo(result.etag());

            verify(persistence, times(1)).persistCompleted(fileId, sessionId, "etag-final");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void completeMultipartUpload_throwsWhenSessionExpired() {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            var ns = new NamespaceContext("svc-a");
            var uploadId = "upload-expired";
            var parts = List.of(new FilePart("etag-1", 1));

            when(persistence.persistCompleting(uploadId))
                    .thenThrow(new EntityNotFoundException("Upload session not found with id: expired"));

            var service = new FileService(
                    fileRepository,
                    sessionRepository,
                    persistence,
                    null,
                    storage,
                    null,
                    durations(),
                    executor
            );

            var request = new CompleteUploadUseCase.CompleteUploadRequest(uploadId, parts);

            assertThatThrownBy(() -> service.completeMultipartUpload(ns, request).join()).isInstanceOf(EntityNotFoundException.class);

            verify(persistence, never()).persistCompleted(any(), any(), any());
            verifyNoInteractions(fileRepository, storage);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void completeMultipartUpload_throwsWhenFileNotOwned() {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            var ns = new NamespaceContext("svc-a");
            var fileId = UuidCreator.fromString("00000000-0000-0000-0000-000000000011");
            var uploadId = "upload-not-owner";
            var parts = List.of(new FilePart("etag-1", 1));

            var session = mock(UploadSession.class);
            when(session.getFileId()).thenReturn(fileId);
            when(persistence.persistCompleting(uploadId)).thenReturn(session);

            var file = mock(FileObject.class);
            when(fileRepository.findById(fileId)).thenReturn(Optional.of(file));
            when(file.getId()).thenReturn(fileId);
            when(file.getOwner()).thenReturn("svc-b");
            when(file.isOwnedBy(anyString())).thenReturn(false);

            var service = new FileService(
                    fileRepository,
                    sessionRepository,
                    persistence,
                    null,
                    storage,
                    null,
                    durations(),
                    executor
            );

            var request = new CompleteUploadUseCase.CompleteUploadRequest(uploadId, parts);
            assertThatThrownBy(() -> service.completeMultipartUpload(ns, request).join()).isInstanceOf(AccessDeniedException.class);

            verify(fileRepository, times(1)).findById(fileId);
            verify(persistence, never()).persistCompleted(any(), any(), any());
            verifyNoInteractions(storage);
        } finally {
            executor.shutdownNow();
        }
    }

    private static DurationProperties durations() {
        return new DurationProperties(
                new DurationProperties.Token(Duration.ofHours(1), Duration.ofMinutes(15)),
                new DurationProperties.Presign(Duration.ofMinutes(10), Duration.ofMinutes(10)),
                new DurationProperties.Multipart(Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofMinutes(5))
        );
    }
}
