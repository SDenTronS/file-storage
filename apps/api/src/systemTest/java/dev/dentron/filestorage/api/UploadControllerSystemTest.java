package dev.dentron.filestorage.api;

import dev.dentron.filestorage.KafkaConfig;
import dev.dentron.filestorage.api.config.DevNoSecurityConfig;
import dev.dentron.filestorage.api.dto.upload.FileMetadataResponseDTO;
import dev.dentron.filestorage.api.dto.upload.PresignedUrlResponseDTO;
import dev.dentron.filestorage.api.dto.upload.UploadCompleteRequestDTO;
import dev.dentron.filestorage.api.dto.upload.UploadCompleteResponseDTO;
import dev.dentron.filestorage.api.dto.upload.UploadCreateRequestDTO;
import dev.dentron.filestorage.api.dto.upload.UploadCreateResponseDTO;
import dev.dentron.filestorage.api.dto.upload.UploadPartDTO;
import dev.dentron.filestorage.api.outbox.OutboxPublisher;
import dev.dentron.worker.WorkerConfig;
import dev.dentron.worker.outbox.OutboxListener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
        classes = UploadControllerSystemTest.ApiSystemTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.name=application-systemtest"
        }
)
@AutoConfigureRestTestClient
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UploadControllerSystemTest {

    private static final String SERVICE_ID = "system-user";
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(20);
    private static final RestClient RAW_HTTP = RestClient.builder().build();

    private static boolean environmentStarted;

    @Container
    private static final ComposeContainer ENVIRONMENT = new ComposeContainer(composeFile())
            .withExposedService("db-1", 5432, Wait.forListeningPort())
            .withExposedService("kafka-1", 19092, Wait.forListeningPort())
            .withExposedService("minio-1", 9000, Wait.forListeningPort());

    private static ConfigurableApplicationContext workerContext;

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        if (!environmentStarted) {
            ENVIRONMENT.start();
            environmentStarted = true;
        }
    }

    @BeforeAll
    void startWorker() {
        workerContext = new SpringApplicationBuilder(WorkerSystemTestApplication.class)
                .run(workerArguments());
    }

    @AfterAll
    void stopWorker() {
        if (workerContext != null) {
            workerContext.close();
        }
    }

    @Test
    void uploadsFileEndToEndAndWaitsUntilReady() {
        byte[] payload = "hello-system-test\n".repeat(400_000).getBytes(StandardCharsets.UTF_8);

        UploadCreateResponseDTO createBody = postJson(
                "/v1/uploads",
                new UploadCreateRequestDTO(
                        "/system-test",
                        "assembled.txt",
                        null,
                        false,
                        payload.length
                ),
                UploadCreateResponseDTO.class
        );

        assertThat(createBody.fileId()).isNotNull();
        assertThat(createBody.multipartUploadId()).isNotBlank();

        PresignedUrlResponseDTO presignBody = postWithoutBody(
                "/v1/uploads/" + createBody.multipartUploadId() + "?partNumber=1",
                PresignedUrlResponseDTO.class
        );

        assertThat(presignBody.url()).isNotBlank();
        String partEtag = uploadPart(presignBody.url(), payload);

        UploadCompleteResponseDTO completeBody = postJson(
                "/v1/uploads/" + createBody.multipartUploadId() + "/complete",
                new UploadCompleteRequestDTO(List.of(new UploadPartDTO(partEtag, 1))),
                UploadCompleteResponseDTO.class
        );

        assertThat(completeBody.fileId()).isEqualTo(createBody.fileId());

        outboxPublisher.publishEvents();

        await()
                .atMost(WAIT_TIMEOUT)
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    FileMetadataResponseDTO metadata = getJson(
                            "/v1/files/" + createBody.fileId(),
                            FileMetadataResponseDTO.class
                    );

                    assertThat(metadata.status().name()).isEqualTo("READY");
                    assertThat(metadata.contentType()).isEqualTo("text/plain");
                    assertThat(metadata.size()).isEqualTo(payload.length);
                    assertThat(metadata.originalName()).isEqualTo("assembled.txt");
                });

        PresignedUrlResponseDTO downloadUrl = postWithoutBody(
                "/v1/files/" + createBody.fileId() + "/download-url",
                PresignedUrlResponseDTO.class
        );

        byte[] downloaded = downloadFile(downloadUrl.url());
        assertThat(downloaded).isEqualTo(payload);
    }

    private <T> T getJson(String path, Class<T> responseType) {
        EntityExchangeResult<T> result = restTestClient.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + SERVICE_ID)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(responseType)
                .returnResult();

        return requireBody(result.getResponseBody(), path);
    }

    private <T> T postWithoutBody(String path, Class<T> responseType) {
        EntityExchangeResult<T> result = restTestClient.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + SERVICE_ID)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(responseType)
                .returnResult();

        return requireBody(result.getResponseBody(), path);
    }

    private <T> T postJson(String path, Object body, Class<T> responseType) {
        EntityExchangeResult<T> result = restTestClient.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + SERVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody(responseType)
                .returnResult();

        return requireBody(result.getResponseBody(), path);
    }

    private static <T> T requireBody(T body, String path) {
        if (body == null) {
            throw new IllegalStateException("Empty response body for path: " + path);
        }
        return body;
    }

    private static String uploadPart(String url, byte[] payload) {
        var response = RAW_HTTP.put()
                .uri(URI.create(url))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(payload.length)
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        String etag = response.getHeaders().getETag();
        if (etag == null || etag.isBlank()) {
            throw new IllegalStateException("Missing ETag header");
        }
        return etag;
    }

    private static byte[] downloadFile(String url) {
        var response = RAW_HTTP.get()
                .uri(URI.create(url))
                .retrieve()
                .toEntity(byte[].class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();

        return response.getBody();
    }

    private static File composeFile() {
        try {
            URI resource = Objects.requireNonNull(
                    UploadControllerSystemTest.class.getClassLoader().getResource("compose.system-test.yaml"),
                    "compose.system-test.yaml resource is missing"
            ).toURI();
            return new File(resource);
        }
        catch (URISyntaxException e) {
            throw new IllegalStateException("Failed to resolve compose.system-test.yaml", e);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ConfigurationPropertiesScan(basePackages = {
            "dev.dentron.filestorage.api.config",
            "dev.dentron.filestorage.application",
            "dev.dentron.filestorage.storages3"
    })
    @ComponentScan(basePackages = {
            "dev.dentron.filestorage.api.config",
            "dev.dentron.filestorage.api.controller",
            "dev.dentron.filestorage.api.exception",
            "dev.dentron.filestorage.api.outbox",
            "dev.dentron.filestorage.api.security",
            "dev.dentron.filestorage.application",
            "dev.dentron.filestorage.persistence",
            "dev.dentron.filestorage.storages3"
    })
    @Import({KafkaConfig.class, DevNoSecurityConfig.class})
    static class ApiSystemTestApplication {
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ConfigurationPropertiesScan(basePackages = {
            "dev.dentron.filestorage.application",
            "dev.dentron.filestorage.storages3"
    })
    @ComponentScan(basePackages = {
            "dev.dentron.filestorage.application",
            "dev.dentron.filestorage.persistence",
            "dev.dentron.filestorage.storages3"
    })
    @Import({KafkaConfig.class, WorkerConfig.class, OutboxListener.class})
    static class WorkerSystemTestApplication {
    }

    private static String[] workerArguments() {
        return new String[]{
                "--spring.main.web-application-type=none",
                "--spring.config.name=application-systemtest"
        };
    }
}
