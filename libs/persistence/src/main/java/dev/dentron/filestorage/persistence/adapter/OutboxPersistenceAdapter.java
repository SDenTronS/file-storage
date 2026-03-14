package dev.dentron.filestorage.persistence.adapter;

import dev.dentron.filestorage.application.outbox.OutboxMessage;
import dev.dentron.filestorage.application.outbox.OutboxStatus;
import dev.dentron.filestorage.application.port.out.outbox.OutboxFailMarker;
import dev.dentron.filestorage.application.port.out.outbox.OutboxPort;
import dev.dentron.filestorage.persistence.jpa.repository.OutboxEventJpaRepository;
import dev.dentron.filestorage.persistence.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Repository
public class OutboxPersistenceAdapter implements OutboxPort, OutboxFailMarker {
    private final OutboxEventJpaRepository repository;
    private final OutboxEventMapper mapper;

    @Override
    public void enqueueOutboxEvent(OutboxMessage message) {
        repository.save(mapper.toEntity(message));
    }

    @Override
    @Transactional
    public List<OutboxMessage> claimBatch(int limit) {
        return repository.claimBatch(limit)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void markPublished(List<UUID> messages, Instant publishedAt) {
        if (messages.isEmpty()) {
            return;
        }

        repository.markPublished(messages, publishedAt);
    }

    @Override
    public void markFailed(List<UUID> messages) {
        if (messages.isEmpty()) {
            return;
        }

        repository.updateStatus(messages, OutboxStatus.FAILED);
    }

    @Override
    public void markFailed(UUID id) {

    }
}
