package dev.dentron.filestorage.api.controller;

import dev.dentron.filestorage.api.dto.upload.DownloadTokenResponseDTO;
import dev.dentron.filestorage.api.dto.upload.IssueDownloadTokenRequestDTO;
import dev.dentron.filestorage.api.dto.upload.PresignedUrlResponseDTO;
import dev.dentron.filestorage.api.security.CurrentService;
import dev.dentron.filestorage.api.security.ServiceDetails;
import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.in.IssueDownloadUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@Tag(name = "Downloads")
@RequestMapping("/v1/")
public class DownloadController {
    private final IssueDownloadUseCase issueDownloadUseCase;

    @Operation(summary = "Presign download", description = "Get download URL.")
    @ApiResponse(responseCode = "200", description = "URL generated")
    @ApiResponse(responseCode = "404", description = "File not found")
    @ApiResponse(responseCode = "409", description = "File not ready")
    @ApiResponse(responseCode = "410", description = "File deleted")
    @ApiResponse(responseCode = "423", description = "File quarantined")
    @ApiResponse(responseCode = "502", description = "Storage error")
    @PostMapping("/files/{fileId}/download-url")
    public ResponseEntity<PresignedUrlResponseDTO> presignFile(
            @PathVariable UUID fileId,
            @Parameter(hidden = true)
            @CurrentService ServiceDetails currentService
    ) {
        NamespaceContext ns = new NamespaceContext(currentService.serviceId());
        var request = new IssueDownloadUseCase.PresignedGetRequest(fileId);
        var result = issueDownloadUseCase.presignGet(ns, request);

        return ResponseEntity.ok(
                new PresignedUrlResponseDTO(
                        result.url(),
                        result.expiresAt()
                ));
    }

    @Operation(summary = "Redeem token", description = "Exchange token for URL.")
    @ApiResponse(responseCode = "200", description = "URL returned")
    @ApiResponse(responseCode = "404", description = "File not found")
    @ApiResponse(responseCode = "409", description = "Token used or file not ready")
    @ApiResponse(responseCode = "410", description = "Token expired or file deleted")
    @ApiResponse(responseCode = "423", description = "File quarantined")
    @ApiResponse(responseCode = "502", description = "Storage error")
    @PostMapping("/download-tokens/redeem")
    public ResponseEntity<PresignedUrlResponseDTO> redeemToken(
            @RequestParam("token") String token,
            @Parameter(hidden = true)
            @CurrentService ServiceDetails currentService
    ) {
        NamespaceContext ns = new NamespaceContext(currentService.serviceId());
        var request = new IssueDownloadUseCase.RedeemTokenRequest(token);
        var result = issueDownloadUseCase.redeemToken(ns, request);

        return ResponseEntity.ok(
                new PresignedUrlResponseDTO(
                        result.url(),
                        result.expiresAt()
                ));
    }

    @Operation(summary = "Issue token", description = "Create one-time download token.")
    @ApiResponse(responseCode = "200", description = "Token issued")
    @ApiResponse(responseCode = "404", description = "File not found")
    @ApiResponse(responseCode = "409", description = "File not ready")
    @ApiResponse(responseCode = "410", description = "File deleted")
    @ApiResponse(responseCode = "423", description = "File quarantined")
    @PostMapping("/files/{fileId}/download-token")
    public ResponseEntity<DownloadTokenResponseDTO> issueDownloadToken(
            @PathVariable UUID fileId,
            @RequestBody @Valid IssueDownloadTokenRequestDTO request,
            @Parameter(hidden = true)
            @CurrentService ServiceDetails currentService
    ) {
        NamespaceContext ns = new NamespaceContext(currentService.serviceId());
        var tokenRequest = new IssueDownloadUseCase.IssueTokenRequest(
                request.audienceService(),
                fileId
        );
        var result = issueDownloadUseCase.issueDownloadToken(ns, tokenRequest);

        return ResponseEntity.ok(
                new DownloadTokenResponseDTO(
                        result.token(),
                        result.expiresAt()
                ));
    }
}
