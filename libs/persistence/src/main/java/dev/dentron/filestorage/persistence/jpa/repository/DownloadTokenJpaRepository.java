package dev.dentron.filestorage.persistence.jpa.repository;

import dev.dentron.filestorage.persistence.jpa.entity.DownloadTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface DownloadTokenJpaRepository extends JpaRepository<DownloadTokenEntity, Long> {
    Optional<DownloadTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query(value =
            " UPDATE download_token " +
            " SET redeemed_at = :now, redeemed_by_service = :redeemer" +
            " WHERE token_hash = :tokenHash" +
            "       AND redeemed_by_service IS NULL " +
            "       AND expires_at > :now" +
            "       AND revoked_at IS NULL " +
            "RETURNING * ", nativeQuery = true)
     Optional<DownloadTokenEntity> tryRedeem(@Param("tokenHash") String tokenHash, @Param("redeemer") String redeemerId, @Param("now") Instant now);
}
