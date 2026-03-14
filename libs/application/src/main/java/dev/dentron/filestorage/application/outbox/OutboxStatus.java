package dev.dentron.filestorage.application.outbox;

import lombok.Getter;

@Getter
public enum OutboxStatus {
    NEW("NEW"),
    CLAIMED("CLAIMED"),
    PUBLISHED("PUBLISHED"),
    FAILED("FAILED");

    private final String status;

    OutboxStatus(String s) {
        this.status = s;
    }
}
