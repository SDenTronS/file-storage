package dev.dentron.filestorage.api.dto.upload;

import java.time.Instant;
import java.util.UUID;

public record UploadCreateResponseDto(
        UUID id,
        String multipartUploadId,
        UUID fileId,
        Instant expiresAt
) {}
