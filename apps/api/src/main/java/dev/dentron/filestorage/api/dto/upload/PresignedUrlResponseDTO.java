package dev.dentron.filestorage.api.dto.upload;

import java.time.Instant;

public record PresignedUrlResponseDTO(
        String url,
        Instant expiresAt
) {}
