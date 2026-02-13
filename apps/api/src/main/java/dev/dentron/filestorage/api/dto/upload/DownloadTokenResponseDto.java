package dev.dentron.filestorage.api.dto.upload;

import java.time.Instant;

public record DownloadTokenResponseDto(
        String token,
        Instant expiresAt
) {}
