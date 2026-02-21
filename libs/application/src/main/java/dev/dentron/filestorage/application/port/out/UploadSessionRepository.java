package dev.dentron.filestorage.application.port.out;

import dev.dentron.filestorage.domain.UploadSession;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadSessionRepository extends CrudRepo<UploadSession, UUID> {
    Optional<UploadSession> findByMultipartUploadId(String multipartUploadId);

    Optional<UploadSession> findByFileId(UUID fileId);

    Optional<UUID> findIdByMultipartUploadId(String multipartUploadId);

    Optional<UUID> findIdByFileId(UUID fileId);

    Optional<UploadSession> tryMarkCompleting(UUID sessionId);

    Optional<SessionView> tryMarkPartsUploaded(UUID sessionId);

    Optional<SessionView> tryMarkCompleted(UUID sessionId);

    Optional<UploadSession> tryMarkAborting(UUID sessionId);

    Optional<SessionView> tryMarkAborted(UUID sessionId);

    List<AbortRow> findExpiredNonAbortedByTimeForUpdate(int limit);

    interface AbortRow {
        UUID sessionId();
        UUID fileId();
        String multipartUploadId();
    }

    interface SessionView {
        UUID getId();
        String getMultipartUploadId();
        UUID getFileId();
        Instant getExpiresAt();
    }

    List<UUID> markAborted(Collection<UUID> ids);

    List<UUID> markAborted(String multipartUploadId);
}
