package dev.dentron.filestorage.application.outbox.payload;

public record FileDeletedPayload (
        String bucket,
        String uploadId,
        String objectKey
) {

}
