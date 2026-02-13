package dev.dentron.filestorage.application.outbox;

import lombok.Getter;

@Getter
public enum OutboxEventType {
    FILE_UPLOADED("file.uploaded.v1"),
    FILE_DELETED("file.deleted.v1");

    private final String eventType;

    OutboxEventType(String eventType) {
        this.eventType = eventType;
    }
}
