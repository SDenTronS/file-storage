package dev.dentron.filestorage.api.dto.upload;

import java.time.Instant;

public record DownloadTokenResponseDTO(
        String token,
        Instant expiresAt
) {}
