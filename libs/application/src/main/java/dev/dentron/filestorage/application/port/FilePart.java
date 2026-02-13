package dev.dentron.filestorage.application.port;

public record FilePart(
        String etag,
        int partNumber
) {}
