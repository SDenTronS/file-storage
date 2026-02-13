package dev.dentron.filestorage.application.port.out.outbox;

import dev.dentron.filestorage.application.outbox.OutboxMessage;

import java.util.List;
import java.util.UUID;

public interface OutboxPort {

    void enqueueOutboxEvent(OutboxMessage message);

    List<OutboxMessage> findNew(int limit);

    void markPublished(List<UUID> messages);

}
