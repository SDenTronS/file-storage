package dev.dentron.filestorage.api.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Schema(
        name = "IssueDownloadTokenRequest",
        description = "Request payload for issuing a download token to another service."
)
public record IssueDownloadTokenRequestDTO(
        @NotBlank
        @Schema(
                description = "Identifier of the service that is allowed to redeem the issued download token.",
                example = "media-service",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String audienceService
) {}
