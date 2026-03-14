package dev.dentron.filestorage.application.port.in;

import dev.dentron.filestorage.application.port.NamespaceContext;

import java.util.UUID;

public interface AbortUploadUseCase {
    void abortUpload(NamespaceContext ns, AbortUploadRequest request);

    record AbortUploadRequest(
            UUID sessionId
    ) {}
}
