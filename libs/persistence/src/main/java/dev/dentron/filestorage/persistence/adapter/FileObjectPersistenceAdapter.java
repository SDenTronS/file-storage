package dev.dentron.filestorage.persistence.adapter;

import dev.dentron.filestorage.application.port.out.FileObjectRepository;
import dev.dentron.filestorage.domain.FileObject;
import dev.dentron.filestorage.persistence.jpa.entity.FileEntity;
import dev.dentron.filestorage.persistence.jpa.repository.FileJpaRepository;
import dev.dentron.filestorage.persistence.mapper.FileObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor

@Repository
public class FileObjectPersistenceAdapter implements FileObjectRepository {
    private final FileJpaRepository repository;
    private final FileObjectMapper mapper;

    @Override
    public FileObject save(FileObject domain) {
        FileEntity entity = mapper.toEntity(domain);
        return mapper.toDomain(repository.save(entity));
    }

    @Override
    public FileObject saveAndFlush(FileObject domain) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(domain)));
    }

    @Override
    public Optional<FileObject> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    @Override
    public Optional<FileObject> findForUpdateById(UUID fileId) {
        return repository.findForUpdateById(fileId).map(mapper::toDomain);
    }

    @Override
    public Optional<FileView> findViewById(UUID fileId) {
        return repository.findViewById(fileId);
    }

    @Override
    public List<FileObject> findAllByIds(List<UUID> ids) {
        return repository.findAllById(ids).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<FileView> tryMarkUploaded(UUID fileId, String etag) {
        return repository.tryTransition(fileId,
                FileObject.Status.UPLOADED.name(),
                List.of(
                        FileObject.Status.UPLOADING.name(),
                        FileObject.Status.UPLOADED.name()
                ),
                etag,
                null);
    }

    @Override
    public Optional<FileView> tryMarkReady(UUID fileId) {
        return repository.tryTransition(fileId,
                FileObject.Status.READY.name(),
                List.of(
                        FileObject.Status.UPLOADED.name(),
                        FileObject.Status.READY.name()
                ),
                null,
                null);
    }

    @Override
    public Optional<FileView> tryMarkRejected(UUID fileId) {
        return repository.tryTransition(fileId,
                FileObject.Status.REJECTED.name(),
                List.of(
                        FileObject.Status.UPLOADED.name(),
                        FileObject.Status.READY.name(),
                        FileObject.Status.QUARANTINED.name(),
                        FileObject.Status.REJECTED.name()
                ),
                null,
                null);
    }

    @Override
    public Optional<FileView> tryMarkDeleted(UUID fileId, Instant deletedAt) {
        return repository.tryTransition(fileId,
                FileObject.Status.DELETED.name(),
                List.of(
                        FileObject.Status.UPLOADING.name(),
                        FileObject.Status.UPLOADED.name(),
                        FileObject.Status.READY.name(),
                        FileObject.Status.QUARANTINED.name(),
                        FileObject.Status.REJECTED.name(),
                        FileObject.Status.DELETED.name()
                ),
                null,
                deletedAt);
    }
}
