package dev.dentron.filestorage.application.port.out;

import dev.dentron.filestorage.domain.FileObject;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileObjectRepository extends CrudRepo<FileObject, UUID> {
    Optional<FileObject> findForUpdateById(UUID fileId);

    Optional<FileView> findViewById(UUID fileId);

    List<FileObject> findAllByOwnerOrderByCreatedAtDesc(String owner);

    List<FileObject> findAllByIds(List<UUID> ids);

    FileObjectScrollPage scrollByOwner(String owner, FileObjectScrollCursor cursor, int limit);

    List<FileObject> findLimitByOwner(String owner, long offset, int limit);

    Optional<FileView> tryMarkUploaded(UUID fileId, String etag);

    Optional<FileView> tryMarkReady(UUID fileId, String contentType, Long size);

    Optional<FileView> tryMarkRejected(UUID fileId);

    Optional<FileView> tryMarkDeleted(UUID fileId, Instant deletedAt);

    record FileObjectScrollPage(
            List<FileObject> items,
            FileObjectScrollCursor nextCursor
    ) {}

    record FileObjectScrollCursor (
            String createdAt,
            String originalName,
            UUID id
    ) {}

    interface FileView {
        UUID getId();
        String getBucket();
        String getObjectKey();
        String getContentType();
        String getOriginalName();
        FileObject.Status getStatus();
    }
}
