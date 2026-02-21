package dev.dentron.filestorage.persistence.jpa.repository;

import dev.dentron.filestorage.application.outbox.OutboxStatus;
import dev.dentron.filestorage.persistence.jpa.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

// TODO статус PROCESSING и метка publishedAt
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query(value = "SELECT * FROM outbox_event e WHERE e.status = 'NEW' ORDER BY e.created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEventEntity> findNewForUpdateOrderByCreatedAtAsc(@Param("limit") int limit);

    @Modifying
    @Query("UPDATE OutboxEventEntity e SET e.status = :status WHERE e.id IN :ids")
    void updateStatus(@Param("ids") List<UUID> ids, @Param("status") OutboxStatus status);
}
