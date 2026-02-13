package dev.dentron.filestorage.api.dto.upload;

import java.time.Instant;

public record PresignedUrlResponseDto(
        String url,
        Instant expiresAt
) {}
