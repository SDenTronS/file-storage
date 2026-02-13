package dev.dentron.filestorage.application.outbox;

import lombok.Getter;

@Getter
public enum OutboxStatus {
    NEW("NEW"),
    PROCESSING( "PROCESSING"),
    PUBLISHED( "PUBLISHED"),
    FAILED("FAILED");

    private String status;

    OutboxStatus(String s) {
        this.status = s;
    }
}
