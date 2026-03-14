package dev.dentron.filestorage.application.port.out;

import dev.dentron.filestorage.domain.DownloadToken;

import java.time.Instant;
import java.util.Optional;

public interface DownloadTokenRepository extends CrudRepo<DownloadToken, Long> {
    Optional<DownloadToken> findByTokenHash(String tokenHash);

    Optional<DownloadToken> tryRedeem(String tokenHash, String redeemerId, Instant redeemTime);
}
