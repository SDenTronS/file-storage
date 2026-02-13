package dev.dentron.filestorage.application.port.in;

import dev.dentron.filestorage.application.port.NamespaceContext;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface DeleteFileUseCase {
    void deleteFile(NamespaceContext ns, DeleteFileRequest request);

    record DeleteFileRequest (
            UUID fileId
    ) {}
}
