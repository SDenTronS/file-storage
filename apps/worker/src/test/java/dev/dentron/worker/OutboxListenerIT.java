package dev.dentron.worker;

import dev.dentron.filestorage.application.outbox.AggregateType;
import dev.dentron.filestorage.application.outbox.OutboxEventType;
import dev.dentron.filestorage.application.outbox.OutboxMessage;
import dev.dentron.filestorage.application.outbox.payload.FileUploadedPayload;
import dev.dentron.filestorage.application.port.out.FileObjectRepository;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort.ObjectMetadata;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort.StorageObject;
import dev.dentron.filestorage.application.port.out.UploadSessionRepository;
import dev.dentron.filestorage.domain.FileObject;
import dev.dentron.filestorage.domain.UploadSession;
import dev.dentron.filestorage.messagingkafka.KafkaTopicsProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@Slf4j
@SpringBootTest(classes = WorkerApplication.class, properties = {
        "app.minio.enabled=false",
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
public class OutboxListenerIT {

    @MockitoBean private ObjectStoragePort storage;
    @MockitoBean private Tika tika;

    @Autowired
    private KafkaTemplate<String, OutboxMessage> kafkaTemplate;

    @Autowired
    private FileObjectRepository fileRepository;

    @Autowired
    private UploadSessionRepository sessionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTopicsProperties kafkaTopicsProperties;

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5");

    @Container
    @ServiceConnection
    private static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.1.1");

    @BeforeEach
    void setUp() throws Exception {
        Detector detector = mock(Detector.class);
        lenient().when(tika.getDetector()).thenReturn(detector);
        lenient().when(detector.detect(any(InputStream.class), any(Metadata.class))).thenReturn(MediaType.parse("text/plain"));
    }

    @Test
    void listensFileUploadedFromKafkaAndMarksReady() {
        UUID fileId = UUID.randomUUID();
        FileObject file = new FileObject(fileId, "svc-a", "uploads/" + fileId, "example.txt", "bucket-main");
        file.markUploaded();
        fileRepository.save(file);

        UploadSession session = new UploadSession(
                UUID.randomUUID(),
                "upload-" + UUID.randomUUID(),
                fileId,
                Instant.now().plusSeconds(3600),
                "text/plain"
        );
        sessionRepository.save(session);

        StorageObject storageObject = new StorageObject(new ByteArrayInputStream("hello".getBytes()), 5);
        when(storage.getObject(any())).thenReturn(CompletableFuture.completedFuture(storageObject));
        when(storage.getObjectMetadata(any())).thenReturn(CompletableFuture.completedFuture(new ObjectMetadata(5, "text/plain")));

        FileUploadedPayload payload = new FileUploadedPayload(fileId);
        var message = new OutboxMessage(
                UUID.randomUUID(),
                OutboxEventType.FILE_UPLOADED,
                objectMapper.writeValueAsString(payload),
                AggregateType.FILE,
                fileId.toString(),
                Instant.now()
        );

        kafkaTemplate.send(kafkaTopicsProperties.topics().fileUploaded().name(), fileId.toString(), message);

        await()
                .pollDelay(Duration.ofSeconds(10))
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    FileObject updated = fileRepository.findById(fileId).orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo(FileObject.Status.READY);
                    assertThat(updated.getContentType()).isEqualTo("text/plain");
                    assertThat(updated.getSize()).isEqualTo(5L);
                });
    }
}
