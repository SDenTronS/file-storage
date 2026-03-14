package dev.dentron.filestorage.api;

import dev.dentron.filestorage.api.config.DevNoSecurityConfig;
import dev.dentron.filestorage.api.controller.DownloadController;
import dev.dentron.filestorage.api.dto.upload.IssueDownloadTokenRequestDTO;
import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.PresignedUrl;
import dev.dentron.filestorage.application.port.in.IssueDownloadUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = DownloadController.class, properties = {
        "app.security.enabled=false"
})
@Import(DevNoSecurityConfig.class)
class DownloadControllerTest extends AbstractControllerWebMvcTestSupport {
    @MockitoBean private IssueDownloadUseCase issueDownloadUseCase;

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
}
