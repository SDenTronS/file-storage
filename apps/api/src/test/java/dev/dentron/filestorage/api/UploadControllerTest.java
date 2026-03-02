package dev.dentron.filestorage.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dentron.filestorage.api.config.DevNoSecurityConfig;
import dev.dentron.filestorage.api.controller.UploadController;
import dev.dentron.filestorage.api.dto.upload.IssueDownloadTokenRequestDTO;
import dev.dentron.filestorage.api.dto.upload.UploadCompleteRequestDTO;
import dev.dentron.filestorage.api.dto.upload.UploadCreateRequestDTO;
import dev.dentron.filestorage.api.dto.upload.UploadPartDTO;
import dev.dentron.filestorage.api.security.ServiceDetails;
import dev.dentron.filestorage.application.port.FilePart;
import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.PresignedUrl;
import dev.dentron.filestorage.application.port.in.CompleteUploadUseCase;
import dev.dentron.filestorage.application.port.in.CreateUploadUseCase;
import dev.dentron.filestorage.application.port.in.DeleteFileUseCase;
import dev.dentron.filestorage.application.port.in.FileQueryUseCase;
import dev.dentron.filestorage.application.port.in.IssueDownloadUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

@WebMvcTest(controllers = UploadController.class, properties = {
        "app.security.enabled=false"
})
@Import(DevNoSecurityConfig.class)
class UploadControllerTest {
    private static final String SERVICE_ID = "dev";

    @MockitoBean private CreateUploadUseCase createUploadUseCase;
    @MockitoBean private CompleteUploadUseCase completeUploadUseCase;
    @MockitoBean private IssueDownloadUseCase issueDownloadUseCase;
    @MockitoBean private DeleteFileUseCase deleteFileUseCase;
    @MockitoBean private FileQueryUseCase fileQueryUseCase;

    @Autowired
    private MockMvcTester mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @Test
    void presignFileReturnsDownloadUrl() {
        var fileId = UUID.randomUUID();
        var expiresAt = Instant.parse("2000-01-01T00:00:00Z");

        when(issueDownloadUseCase.presignGet(any(), any()))
                .thenReturn(new PresignedUrl("https://example.test/download", expiresAt));

        var request = mockMvc.post()
                .uri("/v1/files/{fileId}/download-url", fileId)
                .with(serviceAuthentication());

        var bodyJson = assertThat(request)
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson();

        bodyJson.extractingPath("$.url").isEqualTo("https://example.test/download");
        bodyJson.extractingPath("$.expiresAt").isEqualTo(expiresAt.toString());

        var nsCaptor = ArgumentCaptor.forClass(NamespaceContext.class);
        var requestCaptor = ArgumentCaptor.forClass(IssueDownloadUseCase.PresignedGetRequest.class);

        verify(issueDownloadUseCase).presignGet(nsCaptor.capture(), requestCaptor.capture());

        assertThat(nsCaptor.getValue().serviceId()).isEqualTo(SERVICE_ID);
        assertThat(requestCaptor.getValue().fileId()).isEqualTo(fileId);
    }

    @Test
    void getFileReturnsReducedMetadata() {
        var fileId = UUID.randomUUID();
        var createdAt = Instant.parse("2000-01-01T00:00:00Z");

        when(fileQueryUseCase.getFile(any(), any()))
                .thenReturn(new FileQueryUseCase.FileMetadata(
                        fileId,
                        SERVICE_ID,
                        "bucket-main",
                        "uploads/" + fileId,
                        "document.txt",
                        128L,
                        "sha-256",
                        "etag-123",
                        MediaType.TEXT_PLAIN_VALUE,
                        dev.dentron.filestorage.domain.FileObject.Status.READY,
                        createdAt
                ));

        var request = mockMvc.get()
                .uri("/v1/files/{fileId}", fileId)
                .with(serviceAuthentication());

        var bodyJson = assertThat(request)
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson();

        bodyJson.extractingPath("$.fileId").isEqualTo(fileId.toString());
        bodyJson.extractingPath("$.originalName").isEqualTo("document.txt");
        bodyJson.extractingPath("$.size").isEqualTo(128);
        bodyJson.extractingPath("$.contentType").isEqualTo(MediaType.TEXT_PLAIN_VALUE);
        bodyJson.extractingPath("$.status").isEqualTo("READY");
        bodyJson.extractingPath("$.createdAt").isEqualTo(createdAt.toString());

        var nsCaptor = ArgumentCaptor.forClass(NamespaceContext.class);
        var requestCaptor = ArgumentCaptor.forClass(FileQueryUseCase.GetFileRequest.class);

        verify(fileQueryUseCase).getFile(nsCaptor.capture(), requestCaptor.capture());

        assertThat(nsCaptor.getValue().serviceId()).isEqualTo(SERVICE_ID);
        assertThat(requestCaptor.getValue().fileId()).isEqualTo(fileId);
    }

    @Test
    void redeemTokenReturnsDownloadUrl() {
        var expiresAt = Instant.parse("2000-01-01T00:00:00Z");

        when(issueDownloadUseCase.redeemToken(any(), any()))
                .thenReturn(new PresignedUrl("https://example.test/redeem", expiresAt));

        var request = mockMvc.post()
                .uri("/v1/download-tokens/redeem")
                .with(serviceAuthentication())
                .queryParam("token", "redeem-me");

        var bodyJson = assertThat(request)
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson();

        bodyJson.extractingPath("$.url").isEqualTo("https://example.test/redeem");
        bodyJson.extractingPath("$.expiresAt").isEqualTo(expiresAt.toString());

        var nsCaptor = ArgumentCaptor.forClass(NamespaceContext.class);
        var requestCaptor = ArgumentCaptor.forClass(IssueDownloadUseCase.RedeemTokenRequest.class);

        verify(issueDownloadUseCase).redeemToken(nsCaptor.capture(), requestCaptor.capture());

        assertThat(nsCaptor.getValue().serviceId()).isEqualTo(SERVICE_ID);
        assertThat(requestCaptor.getValue().token()).isEqualTo("redeem-me");
    }

    @Test
    void issueDownloadTokenReturnsToken() throws Exception {
        var fileId = UUID.randomUUID();
        var expiresAt = Instant.parse("2000-01-01T00:00:00Z");
        var requestBody = new IssueDownloadTokenRequestDTO("worker-service");

        when(issueDownloadUseCase.issueDownloadToken(any(), any()))
                .thenReturn(new IssueDownloadUseCase.DownloadTokenResponse("token-123", expiresAt));

        var request = mockMvc.post()
                .uri("/v1/files/{fileId}/download-token", fileId)
                .with(serviceAuthentication())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody));

        var bodyJson = assertThat(request)
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson();

        bodyJson.extractingPath("$.token").isEqualTo("token-123");
        bodyJson.extractingPath("$.expiresAt").isEqualTo(expiresAt.toString());

        var nsCaptor = ArgumentCaptor.forClass(NamespaceContext.class);
        var requestCaptor = ArgumentCaptor.forClass(IssueDownloadUseCase.IssueTokenRequest.class);

        verify(issueDownloadUseCase).issueDownloadToken(nsCaptor.capture(), requestCaptor.capture());

        assertThat(nsCaptor.getValue().serviceId()).isEqualTo(SERVICE_ID);
        assertThat(requestCaptor.getValue().audienceService()).isEqualTo("worker-service");
        assertThat(requestCaptor.getValue().fileId()).isEqualTo(fileId);
    }

    @Test
    void issueDownloadTokenReturnsBadRequestForBlankAudience() throws Exception {
        var fileId = UUID.randomUUID();
        var requestBody = new IssueDownloadTokenRequestDTO(" ");

        var request = mockMvc.post()
                .uri("/v1/files/{fileId}/download-token", fileId)
                .with(serviceAuthentication())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody));

        var bodyJson = assertThat(request)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .doesNotContainHeader(HttpHeaders.LOCATION)
                .bodyJson();

        bodyJson.extractingPath("$.errors").asArray().isNotEmpty();

        verify(issueDownloadUseCase, never()).issueDownloadToken(any(), any());
    }

    @Test
    void deleteFileReturnsNoContent() {
        var fileId = UUID.randomUUID();

        var request = mockMvc.delete()
                .uri("/v1/files/{fileId}", fileId)
                .with(serviceAuthentication());

        assertThat(request)
                .hasStatus(HttpStatus.NO_CONTENT)
                .body().isEmpty();

        var nsCaptor = ArgumentCaptor.forClass(NamespaceContext.class);
        var requestCaptor = ArgumentCaptor.forClass(DeleteFileUseCase.DeleteFileRequest.class);

        verify(deleteFileUseCase).deleteFile(nsCaptor.capture(), requestCaptor.capture());

        assertThat(nsCaptor.getValue().serviceId()).isEqualTo(SERVICE_ID);
        assertThat(requestCaptor.getValue().fileId()).isEqualTo(fileId);
    }

    private RequestPostProcessor serviceAuthentication() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new ServiceDetails(SERVICE_ID),
                null,
                List.of()
        );
        return authentication(authentication);
    }
}
