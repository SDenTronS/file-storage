package dev.dentron.filestorage.application.port.out;

import dev.dentron.filestorage.domain.FileObject;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileObjectRepository extends CrudRepo<FileObject, UUID> {
    Optional<FileObject> findForUpdateById(UUID fileId);

    Optional<FileView> findViewById(UUID fileId);

    List<FileObject> findAllByIds(List<UUID> ids);

    Optional<FileView> tryMarkUploaded(UUID fileId, String etag);

    Optional<FileView> tryMarkReady(UUID fileId);

    Optional<FileView> tryMarkRejected(UUID fileId);

    Optional<FileView> tryMarkDeleted(UUID fileId, Instant deletedAt);

    interface FileView {
        UUID getId();
        String getBucket();
        String getObjectKey();
        String getContentType();
        String getOriginalName();
    }
}
