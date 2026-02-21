package dev.dentron.filestorage.application.port;

import java.time.Instant;

public record PresignedUrl (
        String url,
        Instant expiresAt
) {
}
