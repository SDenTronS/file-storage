package dev.dentron.filestorage.application.port.out.outbox;

import dev.dentron.filestorage.application.outbox.OutboxMessage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxPort {

    void enqueueOutboxEvent(OutboxMessage message);

    List<OutboxMessage> claimBatch(int limit);

    void completeBatch(List<UUID> publishedMessages, List<UUID> failedMessages, List<UUID> retryableMessages, Instant publishedAt);

    void markPublished(List<UUID> messages, Instant publishedAt);

    void markFailed(List<UUID> messages);

    void unclaim(List<UUID> messages);

}
