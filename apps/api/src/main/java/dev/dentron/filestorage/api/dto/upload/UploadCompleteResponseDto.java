package dev.dentron.filestorage.api.dto.upload;

import java.util.UUID;

public record UploadCompleteResponseDto(
        UUID fileId,
        String etag
) {}
