package dev.dentron.filestorage.api.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(
        name = "FileMetadataPageResponse",
        description = "A page of file metadata items with an opaque cursor for the next page."
)
public record FileMetadataPageResponseDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<FileMetadataResponseDTO> items,

        String nextCursor
) {}
