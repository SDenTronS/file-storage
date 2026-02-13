package dev.dentron.filestorage.persistence.adapter;

import dev.dentron.filestorage.application.port.out.outbox.OutboxFailMarker;
import dev.dentron.filestorage.application.outbox.OutboxMessage;
import dev.dentron.filestorage.application.port.out.outbox.OutboxPort;
import dev.dentron.filestorage.application.outbox.OutboxStatus;
import dev.dentron.filestorage.persistence.jpa.repository.OutboxEventJpaRepository;
import dev.dentron.filestorage.persistence.mapper.OutboxEventMapper;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
    public List<OutboxMessage> findNew(int limit) {
        return repository.findNewForUpdateOrderByCreatedAtAsc(limit)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void markPublished(List<UUID> messages) {
        repository.updateStatus(messages, OutboxStatus.PUBLISHED);
    }

    @Override
    public void markFailed(List<UUID> messages) {
        repository.updateStatus(messages, OutboxStatus.FAILED);
    }

    @Override
    public void markFailed(UUID id) {

    }

}
