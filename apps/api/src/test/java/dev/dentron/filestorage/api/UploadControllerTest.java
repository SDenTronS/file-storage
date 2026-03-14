package dev.dentron.filestorage.api;

import dev.dentron.filestorage.api.config.DevNoSecurityConfig;
import dev.dentron.filestorage.api.controller.UploadController;
import dev.dentron.filestorage.api.dto.upload.UploadCompleteRequestDTO;
import dev.dentron.filestorage.api.dto.upload.UploadCreateRequestDTO;
import dev.dentron.filestorage.api.dto.upload.UploadPartDTO;
import dev.dentron.filestorage.application.port.FilePart;
import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.PresignedUrl;
import dev.dentron.filestorage.application.port.in.AbortUploadUseCase;
import dev.dentron.filestorage.application.port.in.CompleteUploadUseCase;
import dev.dentron.filestorage.application.port.in.CreateUploadUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.unit.DataSize;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = UploadController.class, properties = {
        "app.security.enabled=false"
})
@Import(DevNoSecurityConfig.class)
class UploadControllerTest extends AbstractControllerWebMvcTestSupport {
    @MockitoBean private AbortUploadUseCase abortUploadUseCase;
    @MockitoBean private CreateUploadUseCase createUploadUseCase;
    @MockitoBean private CompleteUploadUseCase completeUploadUseCase;

    @Test
    void createUploadReturnsSessionAndPassesPrincipalContext() throws Exception {
        var requestBody = new UploadCreateRequestDTO(
                "/photos",
                "skibidi.png",
                MediaType.IMAGE_PNG_VALUE,
                false,
                DataSize.ofKilobytes(896).toBytes()
        );
        var sessionId = UUID.randomUUID();
        var fileId = UUID.randomUUID();
        var expiresAt = Instant.parse("2000-01-01T00:00:00Z");

        when(createUploadUseCase.createMultipartUploadSessionAsync(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new CreateUploadUseCase.CreateUploadResult(
                                sessionId,
                                "multipart-123",
                                fileId,
                                expiresAt
                        )));

        var request = mockMvc.post()
                .uri("/v1/uploads")
                .with(serviceAuthentication())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody));

        var bodyJson = assertThat(request)
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson();

        bodyJson.extractingPath("$.id").isEqualTo(sessionId.toString());
        bodyJson.extractingPath("$.multipartUploadId").isEqualTo("multipart-123");
        bodyJson.extractingPath("$.fileId").isEqualTo(fileId.toString());
        bodyJson.extractingPath("$.expiresAt").isEqualTo(expiresAt.toString());

        var nsCaptor = ArgumentCaptor.forClass(NamespaceContext.class);
        var requestCaptor = ArgumentCaptor.forClass(CreateUploadUseCase.CreateUploadRequest.class);

        verify(createUploadUseCase).createMultipartUploadSessionAsync(nsCaptor.capture(), requestCaptor.capture());
        assertThat(nsCaptor.getValue().serviceId()).isEqualTo(SERVICE_ID);
        assertThat(requestCaptor.getValue().prefix()).isEqualTo("/photos");
        assertThat(requestCaptor.getValue().originalFileName()).isEqualTo("skibidi.png");
        assertThat(requestCaptor.getValue().sizeBytes()).isEqualTo(DataSize.ofKilobytes(896).toBytes());
    }

    @Test
    void createUploadReturnsBadRequestForInvalidPayload() throws Exception {
        var requestBody = new UploadCreateRequestDTO(
                "/Invalid",
                "broken.png",
                MediaType.IMAGE_PNG_VALUE,
                false,
                0
        );

        var request = mockMvc.post()
                .uri("/v1/uploads")
                .with(serviceAuthentication())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody));

        var bodyJson = assertThat(request)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson();

        bodyJson.extractingPath("$.errors").asArray().isNotEmpty();

        verify(createUploadUseCase, never()).createMultipartUploadSessionAsync(any(), any());
    }

    @Test
    void uploadMultipartReturnsPresignedUrl() {
        var expiresAt = Instant.parse("2000-01-01T00:00:00Z");

        when(createUploadUseCase.presignMultipartPut(any(), any()))
                .thenReturn(new PresignedUrl("https://example.test/upload-part", expiresAt));

        var request = mockMvc.post()
                .uri("/v1/uploads/{uploadId}", "upload-123")
                .queryParam("partNumber", "3")
                .with(serviceAuthentication());

        var bodyJson = assertThat(request)
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson();

        bodyJson.extractingPath("$.url").isEqualTo("https://example.test/upload-part");
        bodyJson.extractingPath("$.expiresAt").isEqualTo(expiresAt.toString());

        var nsCaptor = ArgumentCaptor.forClass(NamespaceContext.class);
        var requestCaptor = ArgumentCaptor.forClass(CreateUploadUseCase.PresignedMultipartPutRequest.class);

        verify(createUploadUseCase).presignMultipartPut(nsCaptor.capture(), requestCaptor.capture());
        assertThat(nsCaptor.getValue().serviceId()).isEqualTo(SERVICE_ID);
        assertThat(requestCaptor.getValue().multipartUploadId()).isEqualTo("upload-123");
        assertThat(requestCaptor.getValue().partNumber()).isEqualTo(3);
    }

    @Test
    void uploadMultipartReturnsBadRequestForNonPositivePartNumber() {
        var request = mockMvc.post()
                .uri("/v1/uploads/{uploadId}", "upload-123")
                .queryParam("partNumber", "0")
                .with(serviceAuthentication());

        assertThat(request).hasStatus(HttpStatus.BAD_REQUEST);

        verify(createUploadUseCase, never()).presignMultipartPut(any(), any());
    }

    @Test
    void abortUploadReturnsNoContent() {
        var sessionId = UUID.randomUUID();

        var request = mockMvc.delete()
                .uri("/v1/uploads/{sessionId}", sessionId)
                .with(serviceAuthentication());

        assertThat(request)
                .hasStatus(HttpStatus.NO_CONTENT)
                .body().isEmpty();

        var nsCaptor = ArgumentCaptor.forClass(NamespaceContext.class);
        var requestCaptor = ArgumentCaptor.forClass(AbortUploadUseCase.AbortUploadRequest.class);

        verify(abortUploadUseCase).abortUpload(nsCaptor.capture(), requestCaptor.capture());
        assertThat(nsCaptor.getValue().serviceId()).isEqualTo(SERVICE_ID);
        assertThat(requestCaptor.getValue().sessionId()).isEqualTo(sessionId);
    }

    @Test
    void completeUploadReturnsFileMetadata() throws Exception {
        var fileId = UUID.randomUUID();
        var requestBody = new UploadCompleteRequestDTO(List.of(
                new UploadPartDTO("etag-1", 1),
                new UploadPartDTO("etag-2", 2)
        ));

        when(completeUploadUseCase.completeMultipartUpload(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new CompleteUploadUseCase.CompleteUploadResult(fileId, "final-etag")));

        var request = mockMvc.post()
                .uri("/v1/uploads/{uploadId}/complete", "upload-123")
                .with(serviceAuthentication())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody));

        var bodyJson = assertThat(request)
                .hasStatus(HttpStatus.OK)
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson();

        bodyJson.extractingPath("$.fileId").isEqualTo(fileId.toString());
        bodyJson.extractingPath("$.etag").isEqualTo("final-etag");

        var nsCaptor = ArgumentCaptor.forClass(NamespaceContext.class);
        var requestCaptor = ArgumentCaptor.forClass(CompleteUploadUseCase.CompleteUploadRequest.class);

        verify(completeUploadUseCase).completeMultipartUpload(nsCaptor.capture(), requestCaptor.capture());
        assertThat(nsCaptor.getValue().serviceId()).isEqualTo(SERVICE_ID);
        assertThat(requestCaptor.getValue().multipartUploadId()).isEqualTo("upload-123");
        assertThat(requestCaptor.getValue().parts()).containsExactly(
                new FilePart("etag-1", 1),
                new FilePart("etag-2", 2)
        );
    }

    @Test
    void completeUploadReturnsBadRequestForEmptyParts() throws Exception {
        var requestBody = new UploadCompleteRequestDTO(List.of());

        var request = mockMvc.post()
                .uri("/v1/uploads/{uploadId}/complete", "upload-123")
                .with(serviceAuthentication())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody));

        var bodyJson = assertThat(request)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson();

        bodyJson.extractingPath("$.errors").asArray().isNotEmpty();

        verify(completeUploadUseCase, never()).completeMultipartUpload(any(), any());
    }
}
