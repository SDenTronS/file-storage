package dev.dentron.filestorage.api.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(
        name = "PresignedUrlResponse",
        description = "Response payload containing a presigned URL and its expiration timestamp."
)
public record PresignedUrlResponseDTO(
        @Schema(
                description = "Presigned URL that can be used to upload or download the file.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String url,


        @Schema(
                description = "Timestamp when the presigned URL expires.",
                type = "string",
                format = "date-time",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Instant expiresAt
) {}
