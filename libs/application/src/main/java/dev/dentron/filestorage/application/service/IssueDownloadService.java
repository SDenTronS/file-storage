package dev.dentron.filestorage.application.service;

import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.PresignedUrl;
import dev.dentron.filestorage.application.port.in.IssueDownloadUseCase;
import dev.dentron.filestorage.application.port.out.DownloadTokenRepository;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort;
import dev.dentron.filestorage.common.util.DownloadTokenUtils;
import dev.dentron.filestorage.domain.DownloadToken;
import dev.dentron.filestorage.domain.FileObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
@Service
public class IssueDownloadService implements IssueDownloadUseCase {
    private final DownloadTokenRepository tokenRepository;
    private final ObjectStoragePort storage;
    private final DownloadTokenUtils tokenUtils;
    private final FileAccessService fileAccessService;
    private final DurationProperties properties;

    @Override
    public PresignedUrl presignGet(NamespaceContext ns, PresignedGetRequest request) {
        FileObject file = fileAccessService.getByIdOwnedBy(request.fileId(), ns);

        Duration ttl = properties.presign().getTtl();
        Instant now = Instant.now();

        return new PresignedUrl(storage.presign(
                new ObjectStoragePort.PresignedRequest(
                        file.getBucket(),
                        file.getObjectKey(),
                        ObjectStoragePort.PresignMethod.GET,
                        Map.of(),
                        ttl
                )
        ), now.plus(ttl));
    }

    @Override
    @Transactional
    public PresignedUrl redeemToken(NamespaceContext redeemer, RedeemTokenRequest request) {
        String hash = tokenUtils.hash(request.token());
        Instant now = Instant.now();
        DownloadToken token = tokenRepository.tryRedeem(hash, redeemer.serviceId(), now)
                .orElseThrow(() -> new IllegalStateException("Token is not valid"));

        FileObject file = fileAccessService.getById(token.getFileId());
        file.ensureAccessible();

        if (!file.getOwner().equals(token.getIssuedByService())) {
            log.error(
                    "token/file ownership mismatch. tokenId={}, fileId={}, fileOwner={}, tokenIssuer={}",
                    token.getId(),
                    token.getFileId(),
                    file.getOwner(),
                    token.getIssuedByService()
            );

            throw new IllegalStateException(
                    "File does not belong to token issuer service. Owner "
                            + file.getOwner()
                            + ", issuer "
                            + token.getIssuedByService()
            );
        }

        if (!redeemer.serviceId().equals(token.getAudienceService())) {
            log.error(
                    "token redeemed by wrong service. tokenId={}, audience={},  redeemedBy={}",
                    token.getId(),
                    token.getAudienceService(),
                    token.getRedeemedByService()
            );

            throw new IllegalStateException(
                    "Redeemed by wrong service, redeemed by "
                            + token.getRedeemedByService()
                            + ", audience "
                            + token.getAudienceService()
            );
        }

        Duration ttl = properties.token().presignTtl();

        return new PresignedUrl(storage.presign(
                new ObjectStoragePort.PresignedRequest(
                        storage.bucket(),
                        file.getObjectKey(),
                        ObjectStoragePort.PresignMethod.GET,
                        Map.of(),
                        ttl
                )
        ), now.plus(ttl));
    }

    @Override
    public DownloadTokenResponse issueDownloadToken(NamespaceContext ns, IssueTokenRequest request) {
        FileObject file = fileAccessService.getByIdOwnedBy(request.fileId(), ns);

        Instant expiresAt = Instant.now().plus(properties.token().ttl());
        String token = tokenUtils.generateToken();
        String hash = tokenUtils.hash(token);

        DownloadToken downloadToken = new DownloadToken(
                hash,
                request.fileId(),
                ns.serviceId(),
                request.audienceService(),
                expiresAt
        );
        tokenRepository.save(downloadToken);
        log.debug(
                "Token issued, id={}, hash={}, fileId={}, issuer={}, redeemer={}, expiresAt={}",
                downloadToken.getFileId(),
                hash,
                request.fileId(),
                ns.serviceId(),
                request.audienceService(),
                expiresAt
        );

        return new DownloadTokenResponse(token, expiresAt);
    }
}
