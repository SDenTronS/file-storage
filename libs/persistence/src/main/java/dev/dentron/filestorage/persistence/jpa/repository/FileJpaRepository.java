package dev.dentron.filestorage.persistence.jpa.repository;

import dev.dentron.filestorage.application.port.out.FileObjectRepository;
import dev.dentron.filestorage.persistence.jpa.entity.FileEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileJpaRepository extends JpaRepository<FileEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FileEntity> findForUpdateById(UUID fileId);

    Optional<FileObjectRepository.FileView> findViewById(@Param("fileId") UUID fileId);

    List<FileEntity> findAllByOwnerOrderByCreatedAtDesc(String owner);

    List<FileEntity> findAllByIdIn(List<UUID> ids);

    Window<FileEntity> findByOwnerOrderByCreatedAtAscOriginalNameAscIdAsc(String owner, KeysetScrollPosition scrollPosition, Limit limit);

    List<FileEntity> findByOwnerOrderByCreatedAtAscOriginalNameAscIdAsc(String owner, OffsetScrollPosition scrollPosition, Limit limit);

    @Query(value = "" +
            "UPDATE file_object " +
            "SET status = :toStatus, " +
            "    etag = COALESCE(etag, :etag), " +
            "    content_type = COALESCE(:contentType, content_type), " +
            "    size = COALESCE(:size, size), " +
            "    deleted_at = COALESCE(deleted_at, :deletedAt) " +
            "WHERE id = :fileId " +
            "     AND status IN (:allowedStatuses) " +
            "RETURNING id, bucket, object_key AS objectKey, content_type AS contentType, original_name AS originalName, status", nativeQuery = true)
    Optional<FileObjectRepository.FileView> tryTransition(@Param("fileId") UUID fileId,
                                                          @Param("toStatus") String toStatus,
                                                          @Param("allowedStatuses") List<String> allowedStatuses,
                                                          @Param("etag") String etag,
                                                          @Param("contentType") String contentType,
                                                          @Param("size") Long size,
                                                          @Param("deletedAt") Instant deletedAt);
}
