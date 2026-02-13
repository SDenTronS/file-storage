package dev.dentron.filestorage.domain.exception;

import dev.dentron.filestorage.domain.UploadSession;

public final class UploadSessionException extends RuntimeException {
    private final Reason reason;
    private final String code;

    public UploadSessionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
        this.code = "UPLOAD_SESSION_" + reason.name();
    }

    public Reason reason() {
        return reason;
    }

    public String code() {
        return code;
    }

    public enum Reason {
        EXPIRED,
        ALREADY_COMPLETED,
        INVALID_STATE
    }
}
