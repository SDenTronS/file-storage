package dev.dentron.worker;

import dev.dentron.filestorage.application.outbox.AggregateType;
import dev.dentron.filestorage.application.outbox.OutboxEventType;
import dev.dentron.filestorage.application.outbox.OutboxMessage;
import dev.dentron.filestorage.application.outbox.payload.FileDeletedPayload;
import dev.dentron.filestorage.application.outbox.payload.FileUploadedPayload;
import dev.dentron.filestorage.application.port.out.FileObjectRepository;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort.ObjectMetadata;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort.StorageObject;
import dev.dentron.filestorage.application.port.out.UploadSessionRepository;
import dev.dentron.filestorage.application.port.out.outbox.OutboxFailMarker;
import dev.dentron.filestorage.application.service.PersistenceService;
import dev.dentron.filestorage.domain.UploadSession;
import dev.dentron.worker.outbox.OutboxListener;
import org.apache.tika.Tika;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutboxListenerTest {
    @Mock private ObjectStoragePort storage;
    @Mock private PersistenceService persistenceService;
    @Mock private UploadSessionRepository sessionRepository;
    @Mock private FileObjectRepository fileRepository;
    @Mock private OutboxFailMarker failMarker;
    @Mock private ObjectMapper mapper;
    @Mock private Tika tika;
    @Spy private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @InjectMocks
    private OutboxListener listener;

    @BeforeEach
    void setUp() throws Exception {
        Detector detector = mock(Detector.class);
        lenient().when(tika.getDetector()).thenReturn(detector);
        lenient().when(detector.detect(any(InputStream.class), any(Metadata.class))).thenReturn(MediaType.parse("text/plain"));
    }

    @Test
    void listen_fileUploaded_persistsReady_whenContentTypeMatches() {
        UUID fileId = UUID.randomUUID();
        String payloadJson = "payload-ok";
        stubFileUploadedPayload(payloadJson, fileId);

        var fileView = mockFileView(fileId, "bucket-a", "obj-key", "example.txt");
        when(fileRepository.findViewById(fileId)).thenReturn(Optional.of(fileView));
        when(sessionRepository.findByFileId(fileId)).thenReturn(Optional.of(uploadSession(fileId, "text/plain")));

        StorageObject storageObject = new StorageObject(new ByteArrayInputStream("hello".getBytes()), 5);
        when(storage.getObject(any())).thenReturn(CompletableFuture.completedFuture(storageObject));
        when(storage.getObjectMetadata(any())).thenReturn(CompletableFuture.completedFuture(new ObjectMetadata(5, "text/plain")));

        listener.listenUploaded(message(OutboxEventType.FILE_UPLOADED, fileId, payloadJson), "key-1").join();

        verify(persistenceService).persistReady(fileId, "text/plain", 5L);
        verify(persistenceService, never()).persistRejected(any());
    }

    @Test
    void listen_fileUploaded_marksRejected_whenDetectionFails() {
        UUID fileId = UUID.randomUUID();
        String payloadJson = "payload-fail";
        stubFileUploadedPayload(payloadJson, fileId);

        var fileView = mockFileView(fileId, "bucket-a", "obj-key", "example.txt");
        when(fileRepository.findViewById(fileId)).thenReturn(Optional.of(fileView));
        when(sessionRepository.findByFileId(fileId)).thenReturn(Optional.of(uploadSession(fileId, "text/plain")));

        when(storage.getObject(any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));

        listener.listenUploaded(message(OutboxEventType.FILE_UPLOADED, fileId, payloadJson), "key-2").join();

        verify(persistenceService).persistRejected(fileId);
        verify(persistenceService, never()).persistReady(any(), any(), any());
        verify(storage, never()).getObjectMetadata(any());
    }

    @Test
    void listen_fileUploaded_marksRejected_andThrows_whenExpectedMismatch() {
        UUID fileId = UUID.randomUUID();
        String payloadJson = "payload-mismatch";
        stubFileUploadedPayload(payloadJson, fileId);

        var fileView = mockFileView(fileId, "bucket-a", "obj-key", "example.txt");
        when(fileRepository.findViewById(fileId)).thenReturn(Optional.of(fileView));
        when(sessionRepository.findByFileId(fileId)).thenReturn(Optional.of(uploadSession(fileId, "image/png")));

        StorageObject storageObject = new StorageObject(new ByteArrayInputStream("hello".getBytes()), 5);
        when(storage.getObject(any())).thenReturn(CompletableFuture.completedFuture(storageObject));

        assertThatThrownBy(() -> listener.listenUploaded(message(OutboxEventType.FILE_UPLOADED, fileId, payloadJson), "key-3").join())
                .hasCauseInstanceOf(IllegalStateException.class);

        verify(persistenceService).persistRejected(fileId);
        verify(persistenceService, never()).persistReady(any(), any(), any());
        verify(storage, never()).getObjectMetadata(any());
    }

    @Test
    void listen_fileDeleted_abortsMultipartUpload() {
        UUID fileId = UUID.randomUUID();
        String payloadJson = "payload-delete";

        when(mapper.readValue(payloadJson, FileDeletedPayload.class))
                .thenReturn(new FileDeletedPayload("bucket-b", "upload-1", "obj-del"));

        when(storage.abortMultipartUploadAsync(any()))
                .thenReturn(CompletableFuture.completedFuture(ObjectStoragePort.AbortResult.ABORTED));

        when(storage.deleteObjectAsync(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        listener.listenDeleted(message(OutboxEventType.FILE_DELETED, fileId, payloadJson), "key-4");

        var requestCaptor = ArgumentCaptor.forClass(ObjectStoragePort.AbortMultipartUploadRequest.class);

        verify(storage).abortMultipartUploadAsync(requestCaptor.capture());
        var request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("bucket-b");
        assertThat(request.objectKey()).isEqualTo("obj-del");
        assertThat(request.uploadId()).isEqualTo("upload-1");

        var deleteRequestCaptor = ArgumentCaptor.forClass(ObjectStoragePort.DeleteObjectRequest.class);
        verify(storage).deleteObjectAsync(deleteRequestCaptor.capture());
        var deleteRequest = deleteRequestCaptor.getValue();
        assertThat(deleteRequest.bucket()).isEqualTo("bucket-b");
        assertThat(deleteRequest.objectKey()).isEqualTo("obj-del");
    }

    @Test
    void listenDlt_marksFailedMessages() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        List<OutboxMessage> messages = List.of(
                new OutboxMessage(first, OutboxEventType.FILE_UPLOADED, "p1", AggregateType.FILE, "a1", Instant.now()),
                new OutboxMessage(second, OutboxEventType.FILE_DELETED, "p2", AggregateType.FILE, "a2", Instant.now())
        );

        listener.listenDlt(messages);

        verify(failMarker).markFailed(List.of(first, second));
    }

    private void stubFileUploadedPayload(String payloadJson, UUID fileId) {
        when(mapper.readValue(payloadJson, FileUploadedPayload.class))
                .thenReturn(new FileUploadedPayload(fileId));
    }

    private FileObjectRepository.FileView mockFileView(UUID fileId, String bucket, String objectKey, String originalName) {
        FileObjectRepository.FileView fileView = mock(
                FileObjectRepository.FileView.class,
                withSettings()
                        .strictness(Strictness.LENIENT)
        );

        when(fileView.getId()).thenReturn(fileId);
        when(fileView.getBucket()).thenReturn(bucket);
        when(fileView.getObjectKey()).thenReturn(objectKey);
        when(fileView.getOriginalName()).thenReturn(originalName);
        return fileView;
    }

    private UploadSession uploadSession(UUID fileId, String expectedContentType) {
        return new UploadSession(UUID.randomUUID(), "upload-1", fileId, Instant.now().plusSeconds(300), expectedContentType);
    }

    private OutboxMessage message(OutboxEventType type, UUID fileId, String payloadJson) {
        return new OutboxMessage(UUID.randomUUID(), type, payloadJson, AggregateType.FILE, fileId.toString(), Instant.now());
    }
}
