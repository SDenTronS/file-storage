package dev.dentron.filestorage.application.port.in;

import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.domain.FileObject;

import java.time.Instant;
import java.util.UUID;

public interface FileQueryUseCase {

    FileMetadata getFile(NamespaceContext ns, GetFileRequest request);

    record GetFileRequest(
            UUID fileId
    ) {}

    record FileMetadata(
            UUID fileId,
            String owner,
            String bucket,
            String objectKey,
            String originalName,
            Long size,
            String sha256,
            String etag,
            String contentType,
            FileObject.Status status,
            Instant createdAt
    ) {}
}
