package dev.dentron.worker;

import dev.dentron.filestorage.application.port.out.FileObjectRepository;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort;
import dev.dentron.filestorage.application.port.out.UploadSessionRepository;
import dev.dentron.filestorage.application.port.out.UploadSessionRepository.AbortRow;
import dev.dentron.filestorage.domain.FileObject;
import dev.dentron.worker.scheduled.ExpirationScanHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExpirationScanHandlerTest {

    @Mock private UploadSessionRepository uploadSessionRepository;
    @Mock private FileObjectRepository fileRepository;
    @Mock private ObjectStoragePort storage;

    private ExpirationScanHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ExpirationScanHandler(uploadSessionRepository, fileRepository, storage);
        when(storage.abortMultipartUploadAsync(any()))
                .thenReturn(CompletableFuture.completedFuture(ObjectStoragePort.AbortResult.ABORTED));
    }

    @Test
    void scanExpiredUploadSessions_abortsSessionsAndUploads() {
        UUID fileId1 = UUID.randomUUID();
        UUID fileId2 = UUID.randomUUID();
        UUID sessionId1 = UUID.randomUUID();
        UUID sessionId2 = UUID.randomUUID();

        var rows = List.of(
                mockAbortRow(sessionId1, fileId1, "upload-1"),
                mockAbortRow(sessionId2, fileId2, "upload-2")
        );
        when(uploadSessionRepository.findExpiredNonAbortedByTimeForUpdate(100)).thenReturn(rows);

        FileObject file1 = new FileObject(fileId1, "svc-a", "obj/1", "a.txt", "bucket-a");
        FileObject file2 = new FileObject(fileId2, "svc-b", "obj/2", "b.txt", "bucket-b");
        when(fileRepository.findAllByIds(List.of(fileId1, fileId2))).thenReturn(List.of(file1, file2));

        handler.scanExpiredUploadSessions();

        ArgumentCaptor<List<UUID>> idsCaptor = ArgumentCaptor.captor();
        verify(uploadSessionRepository).markAborted(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).contains(sessionId1, sessionId2);

        var requestCaptor = ArgumentCaptor.forClass(ObjectStoragePort.AbortMultipartUploadRequest.class);
        verify(storage, times(2)).abortMultipartUploadAsync(requestCaptor.capture());

        assertThat(requestCaptor.getAllValues()).contains(
                new ObjectStoragePort.AbortMultipartUploadRequest("bucket-a", "obj/1", "upload-1"),
                new ObjectStoragePort.AbortMultipartUploadRequest("bucket-b", "obj/2", "upload-2")
        );
    }

    private AbortRow mockAbortRow(UUID sessionId, UUID fileId, String multipartUploadId) {
        AbortRow row = mock(
                AbortRow.class,
                withSettings()
                        .strictness(Strictness.LENIENT)
        );

        when(row.getFileId()).thenReturn(fileId);
        when(row.getSessionId()).thenReturn(sessionId);
        when(row.getMultipartUploadId()).thenReturn(multipartUploadId);

        return row;
    }
}
