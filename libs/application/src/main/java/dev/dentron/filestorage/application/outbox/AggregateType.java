package dev.dentron.filestorage.application.outbox;

import lombok.Getter;

@Getter
public enum AggregateType {
    FILE("file");

    private final String value;

    AggregateType(String value) {
        this.value = value;
    }
}
