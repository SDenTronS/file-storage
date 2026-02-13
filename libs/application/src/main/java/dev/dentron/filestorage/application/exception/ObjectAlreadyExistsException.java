package dev.dentron.filestorage.application.exception;

public final class ObjectAlreadyExistsException extends RuntimeException {
    private final String bucket;
    private final String objectKey;

    public ObjectAlreadyExistsException(String bucket, String objectKey) {
        super("Object already exists: " + bucket + "/" + objectKey);
        this.bucket = bucket;
        this.objectKey = objectKey;
    }

    public String bucket() { return bucket; }
    public String objectKey() { return objectKey; }
}