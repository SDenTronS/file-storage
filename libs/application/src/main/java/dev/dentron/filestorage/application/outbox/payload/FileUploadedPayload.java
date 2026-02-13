package dev.dentron.filestorage.application.outbox.payload;

import java.util.UUID;

public record FileUploadedPayload (
        UUID fileId
) {
}
