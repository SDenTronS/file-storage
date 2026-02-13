package dev.dentron.filestorage.persistence.jpa.repository;

import dev.dentron.filestorage.application.port.out.FileObjectRepository;
import dev.dentron.filestorage.persistence.jpa.entity.FileEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.EnableTransactionManagement;

public interface FileJpaRepository extends JpaRepository<FileEntity, UUID> {
    @Modifying
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FileEntity> findForUpdateById(UUID fileId);

    List<FileEntity> findAllById(List<UUID> ids);

    @Modifying
    @Query(value = "" +
            "UPDATE file_object " +
            "SET status = :toStatus, " +
            "    etag = COALESCE(etag, :etag), " +
            "    deleted_at = COALESCE(deleted_at, :deletedAt) " +
            "WHERE id = :fileId " +
            "     AND status IN (:allowedStatuses) " +
            "RETURNING id, bucket, object_key AS objectKey, content_type AS contentType, original_name AS originalName", nativeQuery = true)
    Optional<FileObjectRepository.FileView> tryTransition(@Param("fileId") UUID fileId,
                                                          @Param("toStatus") String toStatus,
                                                          @Param("allowedStatuses") List<String> allowedStatuses,
                                                          @Param("etag") String etag,
                                                          @Param("deletedAt") Instant deletedAt);
}
