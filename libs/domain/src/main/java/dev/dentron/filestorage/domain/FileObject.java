package dev.dentron.filestorage.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import dev.dentron.filestorage.domain.exception.FileNotAccessibleException;
import lombok.Data;

@Data
public class FileObject {
    private UUID id;
    private String bucket;
    private String owner;
    private String objectKey;
    private String originalName;
    private Long size;
    private String sha256;
    private String etag;
    private String contentType;
    private Status status;
    private Instant createdAt;
    private Instant deletedAt;

    public enum Status {
        UPLOADING,
        UPLOADED,
        READY,
        QUARANTINED,
        REJECTED,
        DELETED
    }

    public FileObject(UUID id, String owner, String objectKey, String originalName, String bucket) {
        this.id = id;
        this.owner = owner;
        this.objectKey = objectKey;
        this.originalName = originalName;
        this.bucket = bucket;
        this.status = Status.UPLOADING;
    }

    public boolean isUploading() {
        return status == Status.UPLOADING;
    }

    public void markDeleted(Instant deletedAt) {
        Objects.requireNonNull(deletedAt, "deletedAt cannot be null");
        if (status == Status.DELETED) {
            return;
        }

        this.status = Status.DELETED;
        this.deletedAt = deletedAt;
    }

    public void markUploaded() {
        if (status == Status.UPLOADED) {
            return;
        }

        if (status != Status.UPLOADING) {
            throw new IllegalStateException("Cannot mark uploaded from status " + status);
        }

        this.status = Status.UPLOADED;
    }


    public void markReady() {
        if (status == Status.READY) {
            return;
        }

        if (status == Status.UPLOADING) {
            throw new IllegalStateException("File object is not uploaded");
        }

        if (status != Status.UPLOADED) {
            throw new IllegalStateException("FileObject is quarantined, rejected, or deleted.");
        }

        this.status = Status.READY;
    }

    public void ensureAccessible() {
        if (status == Status.READY)
            return;

        var reason = switch (status) {
            case UPLOADING, UPLOADED -> FileNotAccessibleException.Reason.NOT_READY;
            case QUARANTINED, REJECTED -> FileNotAccessibleException.Reason.QUARANTINED;
            case DELETED -> FileNotAccessibleException.Reason.DELETED;
            case READY -> throw new IllegalStateException("Unreachable");
        };

        throw new FileNotAccessibleException(id, status, reason);
    }

    public boolean isOwnedBy(String owner) {
        return this.getOwner().equals(owner);
    }
}
