package dev.dentron.filestorage.api.dto.upload;

import java.util.UUID;

public record UploadCompleteResponseDTO(
        UUID fileId,
        String etag
) {}
