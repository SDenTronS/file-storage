package dev.dentron.filestorage.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import dev.dentron.filestorage.domain.exception.DownloadTokenAlreadyUsedException;
import dev.dentron.filestorage.domain.exception.DownloadTokenExpiredException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DownloadToken {
    private Long id;
    private String tokenHash;
    private UUID fileId;
    private String issuedByService;
    private String audienceService;
    private Instant expiresAt;
    private Instant redeemedAt;
    private String redeemedByService;

    private Instant createdAt;
    private Instant revokedAt;

    public DownloadToken(String tokenHash, UUID fileId, String issuedByService, String audienceService, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.fileId = fileId;
        this.issuedByService = issuedByService;
        this.audienceService = audienceService;
        this.expiresAt = expiresAt;
    }

    public static DownloadToken restore(
            Long id,
            String tokenHash,
            UUID fileId,
            String issuedByService,
            String audienceService,
            Instant expiresAt,
            Instant redeemedAt,
            String redeemedByService,
            Instant createdAt,
            Instant revokedAt
    ) {
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(fileId, "fileId");
        Objects.requireNonNull(issuedByService, "issuedByService");
        Objects.requireNonNull(audienceService, "audienceService");
        Objects.requireNonNull(expiresAt, "expiresAt");

        return new DownloadToken(
                id,
                tokenHash,
                fileId,
                issuedByService,
                audienceService,
                expiresAt,
                redeemedAt,
                redeemedByService,
                createdAt,
                revokedAt
        );
    }

    public boolean isUsed() {
        return redeemedByService != null;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public void ensureValid(Instant now) {
        if (isUsed()) {
            throw new DownloadTokenAlreadyUsedException();
        }

        if (isExpired(now)) {
            throw new DownloadTokenExpiredException();
        }
    }

    public void redeem(Instant now, String redeemedByService) {
        ensureValid(now);

        this.redeemedByService = redeemedByService;
        this.redeemedAt = now;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}

