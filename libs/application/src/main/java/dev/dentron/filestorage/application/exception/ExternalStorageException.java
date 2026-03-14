package dev.dentron.filestorage.application.exception;

public class ExternalStorageException extends RuntimeException {
    private final String operation;
    private final String bucket;
    private final String objectKey;
    private final String externalCode;

    public ExternalStorageException(
            String operation,
            String bucket,
            String objectKey,
            String externalCode,
            Throwable cause
    ) {
        this("External storage operation failed", operation, bucket, objectKey, externalCode, cause);
    }

    protected ExternalStorageException(
            String message,
            String operation,
            String bucket,
            String objectKey,
            String externalCode,
            Throwable cause
    ) {
        super(message, cause);
        this.operation = operation;
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.externalCode = externalCode;
    }

    public String operation() { return operation; }

    public String bucket() { return bucket; }

    public String objectKey() { return objectKey; }

    public String externalCode() { return externalCode; }
}
