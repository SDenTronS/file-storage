package dev.dentron.filestorage.api.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(
        name = "FileMetadataResponse",
        description = "Metadata describing the current state of a stored file."
)
public record FileMetadataResponseDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID fileId,

        String originalName,

        Long size,

        String contentType,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        FileStatusResponse status,

        @Schema(type = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt
) {}
