package dev.dentron.filestorage.persistence.jpa.repository;

import dev.dentron.filestorage.application.port.out.UploadSessionRepository;
import dev.dentron.filestorage.persistence.jpa.entity.UploadSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadSessionJpaRepository extends JpaRepository<UploadSessionEntity, UUID> {
    Optional<UploadSessionEntity> findByMultipartUploadId(String multipartUploadId);

    Optional<UploadSessionEntity> findByFileId(UUID fileId);

    @Query(value = "SELECT id FROM upload_session WHERE multipart_upload_id = :multipartUploadId", nativeQuery = true)
    Optional<UUID> findIdByMultipartUploadId(@Param("multipartUploadId") String multipartUploadId);

    @Query(value = "SELECT id FROM upload_session WHERE file_id = :fileId", nativeQuery = true)
    Optional<UUID> findIdByFileId(@Param("fileId") UUID fileId);

    @Modifying
    @Query(value = "" +
            "UPDATE upload_session " +
            "SET status = :toStatus " +
            "WHERE id = :sessionId " +
            "     AND (expires_at > now() OR :allowExpired)" +
            "     AND status IN (:allowedStatuses) " +
            "RETURNING *", nativeQuery = true)
    Optional<UploadSessionEntity> tryTransition(@Param("sessionId") UUID sessionId,
                                                @Param("toStatus") String toStatus,
                                                @Param("allowedStatuses") List<String> allowedStatuses,
                                                @Param("allowExpired") boolean allowExpired);

    @Modifying
    @Query(value = "" +
            "UPDATE upload_session " +
            "SET status = :toStatus " +
            "WHERE multipart_upload_id = :multipartUploadId " +
            "     AND (expires_at > now() OR :allowExpired)" +
            "     AND status IN (:allowedStatuses) " +
            "RETURNING *", nativeQuery = true)
    Optional<UploadSessionEntity> tryTransition(@Param("multipartUploadId") String multipartUploadId,
                                                @Param("toStatus") String toStatus,
                                                @Param("allowedStatuses") List<String> allowedStatuses,
                                                @Param("allowExpired") boolean allowExpired);

    @Modifying
    @Query(value = "" +
            "UPDATE upload_session " +
            "SET status = :toStatus " +
            "WHERE id = :sessionId " +
            "     AND (expires_at > now() OR :allowExpired)" +
            "     AND status IN (:allowedStatuses) " +
            "RETURNING id, multipart_upload_id AS multipartUploadId, file_id AS fileId, expires_at AS expiresAt", nativeQuery = true)
    Optional<UploadSessionRepository.SessionView> tryTransitionView(@Param("sessionId") UUID sessionId,
                                                                    @Param("toStatus") String toStatus,
                                                                    @Param("allowedStatuses") List<String> allowedStatuses,
                                                                    @Param("allowExpired") boolean allowExpired);

    @Query(value = """
        WITH picked AS (
                SELECT u.id FROM upload_session AS u
                WHERE u.expires_at < now() AND u.status NOT IN ('COMPLETED', 'COMPLETING', 'ABORTED', 'ABORTING')
                ORDER BY expires_at ASC LIMIT :limit
                FOR UPDATE SKIP LOCKED
        )
        UPDATE upload_session s
        SET status = 'ABORTING'
        FROM picked
        WHERE s.id = picked.id
        RETURNING id, file_id, multipart_upload_id
        """, nativeQuery = true)
    List<UploadSessionRepository.AbortRow> findExpiredNonAbortedByTimeForUpdate(@Param("limit") int limit);

    @Modifying
    @Query(value = "UPDATE upload_session SET status = 'ABORTED' WHERE id IN (:ids) RETURNING id", nativeQuery = true)
    List<UUID> markAborted(Collection<UUID> ids);

    @Modifying
    @Query(value = "UPDATE upload_session SET status = 'ABORTED' WHERE multipart_upload_id = :multipartUploadId RETURNING id", nativeQuery = true)
    List<UUID> markAborted(String multipartUploadId);

}
