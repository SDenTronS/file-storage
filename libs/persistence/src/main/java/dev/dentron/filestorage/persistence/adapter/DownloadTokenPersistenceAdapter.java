package dev.dentron.filestorage.persistence.adapter;

import dev.dentron.filestorage.application.port.out.DownloadTokenRepository;
import dev.dentron.filestorage.domain.DownloadToken;
import dev.dentron.filestorage.persistence.jpa.entity.DownloadTokenEntity;
import dev.dentron.filestorage.persistence.jpa.repository.DownloadTokenJpaRepository;
import dev.dentron.filestorage.persistence.mapper.DownloadTokenMapper;

import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor

@Repository
public class DownloadTokenPersistenceAdapter implements DownloadTokenRepository {
    private final DownloadTokenJpaRepository repository;
    private final DownloadTokenMapper mapper;

    @Override
    public DownloadToken save(DownloadToken domain) {
        DownloadTokenEntity entity = mapper.toEntity(domain);
        return mapper.toDomain(repository.save(entity));
    }

    @Override
    public DownloadToken saveAndFlush(DownloadToken domain) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(domain)));
    }

    @Override
    public Optional<DownloadToken> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsById(Long id) {
        return repository.existsById(id);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    @Override
    public Optional<DownloadToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    @Override
    public Optional<DownloadToken> tryRedeem(String tokenHash, String redeemerId, Instant redeemTime) {
        return repository.tryRedeem(tokenHash, redeemerId, redeemTime).map(mapper::toDomain);
    }
}