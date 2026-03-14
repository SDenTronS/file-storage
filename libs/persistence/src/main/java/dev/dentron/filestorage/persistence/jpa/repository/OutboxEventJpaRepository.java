package dev.dentron.filestorage.persistence.jpa.repository;

import dev.dentron.filestorage.application.outbox.OutboxStatus;
import dev.dentron.filestorage.persistence.jpa.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query(value = """
            WITH batch AS (
                SELECT e.id
                FROM outbox_event e
                WHERE e.status = 'NEW'
                ORDER BY e.created_at ASC
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            UPDATE outbox_event e
            SET status = 'CLAIMED'
            FROM batch
            WHERE e.id = batch.id
            RETURNING e.*
            """, nativeQuery = true)
    List<OutboxEventEntity> claimBatch(@Param("limit") int limit);

    @Modifying
    @Query(value = """
            UPDATE outbox_event
            SET status = 'PUBLISHED',
                published_at = :publishedAt
            WHERE id IN (:ids)
            """, nativeQuery = true)
    void markPublished(@Param("ids") List<UUID> ids, @Param("publishedAt") Instant publishedAt);

    @Modifying
    @Query("UPDATE OutboxEventEntity e SET e.status = :status WHERE e.id IN :ids")
    void updateStatus(@Param("ids") List<UUID> ids, @Param("status") OutboxStatus status);
}
