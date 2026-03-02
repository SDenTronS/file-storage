package dev.dentron.filestorage.api.dto.upload;

public enum FileStatusResponse {
    UPLOADING,
    UPLOADED,
    READY,
    QUARANTINED,
    REJECTED,
    DELETED
}
