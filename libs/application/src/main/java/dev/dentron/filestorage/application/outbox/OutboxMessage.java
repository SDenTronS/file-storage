package dev.dentron.filestorage.application.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxMessage(
        UUID eventId,
        OutboxEventType eventType,
        String payloadJson,
        AggregateType aggregateType,
        String aggregateId,
        Instant occurredAt
) {
    public static OutboxMessage restore(
            UUID eventId,
            OutboxEventType eventType,
            String payloadJson,
            AggregateType aggregateType,
            String aggregateId,
            Instant occurredAt
    ) {
        return new OutboxMessage(eventId, eventType, payloadJson, aggregateType, aggregateId, occurredAt);
    }
}
