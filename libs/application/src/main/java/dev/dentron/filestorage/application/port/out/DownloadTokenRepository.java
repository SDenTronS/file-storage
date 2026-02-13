package dev.dentron.filestorage.application.port.out;

import dev.dentron.filestorage.domain.DownloadToken;

import java.util.Optional;
import java.util.UUID;

public interface DownloadTokenRepository extends CrudRepo<DownloadToken, Long> {
    Optional<DownloadToken> findByTokenHash(String tokenHash);

    Optional<DownloadToken> tryRedeem(String tokenHash, String redeemerId);
}
