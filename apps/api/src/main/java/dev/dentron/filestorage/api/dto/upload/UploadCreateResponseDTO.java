package dev.dentron.filestorage.api.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(
        name = "UploadCreateResponse",
        description = "Response payload returned after creating a multipart upload session."
)
public record UploadCreateResponseDTO(
        @Schema(
                description = "Upload session identifier.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        UUID id,


        @Schema(
                description = "Multipart upload identifier returned by the underlying object storage. Might be empty if upload is not multipart."
        )
        String multipartUploadId,


        @Schema(
                description = "Identifier of the file entity that will be finalized after upload completion.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        UUID fileId,


        @Schema(
                description = "Timestamp when the multipart upload session expires.",
                type = "string",
                format = "date-time",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Instant expiresAt
) {}
