package dev.dentron.filestorage.application.service;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.PresignedUrl;
import dev.dentron.filestorage.application.port.in.CompleteUploadUseCase;
import dev.dentron.filestorage.application.port.in.CreateUploadUseCase;
import dev.dentron.filestorage.application.port.in.DeleteFileUseCase;
import dev.dentron.filestorage.application.port.in.IssueDownloadUseCase;
import dev.dentron.filestorage.application.port.out.DownloadTokenRepository;
import dev.dentron.filestorage.application.port.out.FileObjectRepository;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort;
import dev.dentron.filestorage.application.port.out.UploadSessionRepository;
import dev.dentron.filestorage.common.util.DownloadTokenUtils;
import dev.dentron.filestorage.domain.DownloadToken;
import dev.dentron.filestorage.domain.FileObject;
import dev.dentron.filestorage.domain.UploadSession;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor

@Slf4j
@Service
public class FileService implements CompleteUploadUseCase, CreateUploadUseCase, DeleteFileUseCase, IssueDownloadUseCase {
    private static final DataSize DATA_SIZE = DataSize.ofGigabytes(5);
    private final FileObjectRepository fileRepository;
    private final UploadSessionRepository sessionRepository;
    private final PersistenceService persistenceService;
    private final DownloadTokenRepository tokenRepository;
    private final ObjectStoragePort storage;
    private final DownloadTokenUtils tokenUtils;
    private final DurationProperties properties;
    private final ExecutorService executor;

    @Override
    public CompletableFuture<CreateUploadResult> createMultipartUploadSessionAsync(NamespaceContext ns, CreateUploadRequest request) {
        UUID fileId = UuidCreator.getTimeBased();
        UUID sessionId = UuidCreator.getTimeBased();

        Instant expiresAt = Instant.now().plus(properties.multipart().sessionTtl());
        String objectKey = ns.root() + request.prefix() + "/" + fileId;
        String bucket = storage.bucket();

        var multipartUploadRequest = new ObjectStoragePort.CreateMultipartUploadRequest(
                bucket,
                storage.region(),
                objectKey
        );

        FileObject file = new FileObject(fileId, ns.serviceId(), objectKey, request.originalFileName(), bucket);

        return storage
                .createMultipartUploadAsync(multipartUploadRequest)
                .orTimeout(properties.multipart().create().toMillis(), TimeUnit.MILLISECONDS)
                .thenApplyAsync(multipartUpload -> {
                    String multipartUploadId = multipartUpload.uploadId();

                    UploadSession session = new UploadSession(
                            sessionId,
                            multipartUploadId,
                            file.getId(),
                            expiresAt,
                            request.expectedContentType()
                    );

                    UUID persistedSessionId = persistenceService.persistSession(file, session);

                    return new CreateUploadResult(
                            persistedSessionId,
                            multipartUploadId,
                            file.getId(),
                            expiresAt);
                }, executor)
                .whenComplete((r, t) -> {
                    if (t != null) {
                        //TODO аборнуть multipart?
                    }
                });
    }

    @Override
    public PresignedUrl presignMultipartPut(NamespaceContext ns, PresignedMultipartPutRequest request) {
        UploadSession session = sessionRepository.findByMultipartUploadId(request.multipartUploadId())
                .orElseThrow(() -> new EntityNotFoundException("Session not found with multipart upload id: " + request.multipartUploadId()));

        return presignPutQuery(ns, session, Map.of(
                "uploadId", request.multipartUploadId(),
                "partNumber", String.valueOf(request.partNumber())
        ));
    }

    @Override
    public PresignedUrl presignPut(NamespaceContext ns, PresignedPutRequest request) {
        UploadSession session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new EntityNotFoundException("Session not found with id: " + request.sessionId()));

        return presignPutQuery(ns, session, Map.of());
    }

    private PresignedUrl presignPutQuery(NamespaceContext ns, UploadSession session, Map<String, String> query) {
        Instant now = Instant.now();
        session.ensureAvailable(now);

        FileObject file = fromIdOwnedBy(session.getFileId(), ns);

        Duration ttl = properties.presign().putTtl();

        return new PresignedUrl(storage.presign(
                new ObjectStoragePort.PresignedRequest(
                        file.getBucket(),
                        file.getObjectKey(),
                        ObjectStoragePort.PresignMethod.PUT,
                        query,
                        ttl
                )
        ), now.plus(ttl));
    }


    @Override
    public CompletableFuture<CompleteUploadResult> completeMultipartUpload(NamespaceContext ns, CompleteUploadRequest request) {
        UploadSession session = persistenceService.persistCompleting(request.multipartUploadId());
        FileObject file = fromIdOwnedBy(session.getFileId(), ns);

        var completeRequest = new ObjectStoragePort.CompleteMultipartRequest(
                storage.bucket(),
                file.getObjectKey(),
                request.multipartUploadId(),
                request.parts()
        );

        //TODO подумать над тем что будет если база наебнется

        return storage
                .completeMultipartUploadAsync(completeRequest)
                .thenApplyAsync((etag) -> {
                    if (etag == null) {
                        return new CompleteUploadResult(file.getId(), null);
                    }

                    persistenceService.persistCompleted(file.getId(), session.getId(), etag);
                    return new CompleteUploadResult(file.getId(), etag);
                }, executor);
    }

    @Override
    public PresignedUrl presignGet(NamespaceContext ns, PresignedGetRequest request) {
        FileObject file = fromIdOwnedBy(request.fileId(), ns);

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

        FileObject file = fromId(token.getFileId());
        file.ensureAccessible();

        if (!file.getOwner().equals(token.getIssuedByService())) {
            log.error(
                    "token/file ownership mismatch. tokenId={}, fileId={}, fileOwner={}, tokenIssuer={}",
                    token.getId(),
                    token.getFileId(),
                    file.getOwner(),
                    token.getIssuedByService()
            );

            throw new IllegalStateException("File does not belong to token issuer service. Owner " + file.getOwner() + ", issuer " + token.getIssuedByService());
        }

        if (!redeemer.serviceId().equals(token.getAudienceService())) {
            log.error("token redeemed by wrong service. tokenId={}, audience={},  redeemedBy={}",
                    token.getId(),
                    token.getAudienceService(),
                    token.getRedeemedByService()
            );

            throw new IllegalStateException("Redeemed by wrong service, redeemed by " + token.getRedeemedByService() + ", audience " + token.getAudienceService());
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
        FileObject file = fromIdOwnedBy(request.fileId(), ns);

        Instant expiresAt = Instant.now().plus(properties.token().ttl());
        String token = tokenUtils.generateToken();
        String hash = tokenUtils.hash(token);

        DownloadToken downloadToken = new DownloadToken(hash, request.fileId(), ns.serviceId(), request.audienceService(), expiresAt);
        tokenRepository.save(downloadToken);
        log.debug("Token issued, id={}, hash={}, fileId={}, issuer={}, redeemer={}, expiresAt={}", downloadToken.getFileId(), hash, request.fileId(), ns.serviceId(), request.audienceService(), expiresAt);

        return new DownloadTokenResponse(token, expiresAt);
    }


    private FileObject fromId(UUID fileId) {
        return fileRepository.findById(fileId).orElseThrow(() -> new EntityNotFoundException("File not found with id: " + fileId));
    }

    private FileObject fromIdOwnedBy(UUID fileId, NamespaceContext ns) {
        FileObject file = fromId(fileId);

        if (!file.isOwnedBy(ns.serviceId())) {
            log.warn("file belongs to different owner. fileId={}, fileOwner={}, callerService={}", file.getId(), file.getOwner(), ns.serviceId());
            throw new AccessDeniedException("Forbidden");
        }

        return file;
    }

    @Override
    public void deleteFile(NamespaceContext ns, DeleteFileRequest request) {
        FileObject file = fromIdOwnedBy(request.fileId(), ns);
        Instant now = Instant.now();
        persistenceService.persistDeleted(file.getId(), now);
    }
}
