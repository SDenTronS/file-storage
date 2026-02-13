package dev.dentron.filestorage.domain.exception;

import dev.dentron.filestorage.domain.FileObject;

import java.util.UUID;

public class FileNotAccessibleException extends RuntimeException {
    private final UUID fileId;
    private final FileObject.Status status;
    private final Reason reason;

    public FileNotAccessibleException(UUID fileId, FileObject.Status status, Reason reason) {
        super("File is not accessible: id=" + fileId + ", status=" + status + ", reason=" + reason);
        this.fileId = fileId;
        this.status = status;
        this.reason = reason;
    }

    public UUID fileId() {
        return fileId;
    }

    public FileObject.Status status() {
        return status;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        NOT_READY,
        QUARANTINED,
        DELETED
    }
}
