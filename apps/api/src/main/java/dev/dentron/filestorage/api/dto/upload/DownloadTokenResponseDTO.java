package dev.dentron.filestorage.api.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(
        name = "DownloadTokenResponse",
        description = "Response payload containing a download token and its expiration timestamp."
)
public record DownloadTokenResponseDTO(
        @Schema(
                description = "Issued token that can be used to redeem access to the file.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String token,


        @Schema(
                description = "Timestamp when the issued download token expires.",
                type = "string",
                format = "date-time",
                example = "2026-02-27T15:04:05Z",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Instant expiresAt
) {}
