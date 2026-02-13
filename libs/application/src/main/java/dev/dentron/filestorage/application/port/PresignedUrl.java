package dev.dentron.filestorage.application.port;

import java.time.Instant;

//TODO добавить
public record PresignedUrl (
        String url,
        Instant expiresAt
) {
}
