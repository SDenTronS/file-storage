package dev.dentron.filestorage.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import dev.dentron.filestorage.domain.exception.UploadSessionException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UploadSession {
    private UUID id;
    private String multipartUploadId;
    private UUID fileId;
    private Instant expiresAt;
    private Long expectedSize;
    private String expectedContentType;
    private Status status;

    public enum Status {
        CREATED,
        PARTS_UPLOADED,
        COMPLETING,
        COMPLETED,
        EXPIRED,
        ABORTED
    }

    public UploadSession(UUID id, String multipartUploadId, UUID fileId, Instant expiresAt, String expectedContentType) {
        this.id = id;
        this.multipartUploadId = multipartUploadId;
        this.fileId = fileId;
        this.expiresAt = expiresAt;
        this.expectedContentType = expectedContentType;
        this.status = Status.CREATED;
    }


    public static UploadSession restore(
            UUID id,
            String multipartUploadId,
            UUID fileId,
            Instant expiresAt,
            Long expectedSize,
            String expectedContentType,
            Status status
    ) {
        return new UploadSession(id, multipartUploadId, fileId, expiresAt, expectedSize, expectedContentType, status);
    }

    public void ensureAvailable(Instant now) {
        Objects.requireNonNull(now, "now");
        ensureNotExpired(now);

        if (status == Status.COMPLETED || status == Status.COMPLETING || status == Status.ABORTED) {
            throw new UploadSessionException(UploadSessionException.Reason.INVALID_STATE, "Session unavailable");
        }
    }

    public void abort(Instant now) {
        Objects.requireNonNull(now, "now");

        if (status == Status.ABORTED) return;

        if (status == Status.COMPLETED || status == Status.COMPLETING) {
            fail(UploadSessionException.Reason.ALREADY_COMPLETED, "Upload session already completed");
        }

        status = Status.ABORTED;
    }

    public void ensureNotExpired(Instant now) {
        Objects.requireNonNull(now, "now");

        if (status == Status.COMPLETED || status == Status.COMPLETING) {
            return;
        }

        if (status == Status.EXPIRED || now.isAfter(expiresAt)) {
            throw new UploadSessionException(UploadSessionException.Reason.EXPIRED, "Upload session has expired");
        }
    }

    public void markCompleting() {
        if (status == Status.COMPLETING) {
            return;
        }
    }

    private void fail(UploadSessionException.Reason reason, String message) {
        throw new UploadSessionException(reason, message);
    }
}
