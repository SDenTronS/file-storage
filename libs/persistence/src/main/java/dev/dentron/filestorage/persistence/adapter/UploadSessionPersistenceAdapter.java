package dev.dentron.filestorage.persistence.adapter;

import dev.dentron.filestorage.application.port.out.UploadSessionRepository;
import dev.dentron.filestorage.domain.UploadSession;
import dev.dentron.filestorage.persistence.jpa.entity.UploadSessionEntity;
import dev.dentron.filestorage.persistence.jpa.repository.UploadSessionJpaRepository;
import dev.dentron.filestorage.persistence.mapper.UploadSessionMapper;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor

@Repository
public class UploadSessionPersistenceAdapter implements UploadSessionRepository {
    private final UploadSessionJpaRepository repository;
    private final UploadSessionMapper mapper;

    @Override
    public UploadSession save(UploadSession domain) {
        UploadSessionEntity entity = mapper.toEntity(domain);
        return mapper.toDomain(repository.save(entity));
    }

    @Override
    public UploadSession saveAndFlush(UploadSession domain) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(domain)));
    }

    @Override
    public Optional<UploadSession> findById(UUID id) {
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
    public Optional<UploadSession> findByMultipartUploadId(String multipartUploadId) {
        return repository.findByMultipartUploadId(multipartUploadId).map(mapper::toDomain);
    }

    @Override
    public Optional<UploadSession> findByFileId(UUID fileId) {
        return repository.findByFileId(fileId).map(mapper::toDomain);
    }

    @Override
    public Optional<UUID> findIdByMultipartUploadId(String multipartUploadId) {
        return repository.findIdByMultipartUploadId(multipartUploadId);
    }

    @Override
    public Optional<UUID> findIdByFileId(UUID fileId) {
        return repository.findIdByFileId(fileId);
    }

    @Override
    public Optional<UploadSession> tryMarkCompleting(UUID sessionId) {
        return repository
                .tryTransition(sessionId, "COMPLETING", List.of("CREATED"), false)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<SessionView> tryMarkCompleted(UUID sessionId) {
        return repository
                .tryTransitionView(sessionId, "COMPLETED", List.of("COMPLETING", "COMPLETED"), true);
    }

    @Override
    public Optional<UploadSession> tryMarkAborting(UUID sessionId) {
        return repository
                .tryTransition(sessionId, "ABORTING", List.of("CREATED", "EXPIRED"), true)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<SessionView> tryMarkAborted(UUID sessionId) {
        return repository
                .tryTransitionView(sessionId, "ABORTED", List.of("CREATED", "EXPIRED", "ABORTING", "ABORTED"), true);
    }

    @Override
    public List<AbortRow> findExpiredNonAbortedByTimeForUpdate(int limit) {
        return repository.findExpiredNonAbortedByTimeForUpdate(limit);
    }

    @Override
    public int markAborted(Collection<UUID> ids) {
        return repository.markAborted(ids);
    }

    @Override
    public List<UUID> markAborted(String multipartUploadId) {
        return repository.markAborted(multipartUploadId);
    }

}
