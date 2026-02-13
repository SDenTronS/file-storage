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
) {}
