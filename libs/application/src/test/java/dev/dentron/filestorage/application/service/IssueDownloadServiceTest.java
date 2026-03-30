package dev.dentron.filestorage.application.service;

import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.out.DownloadTokenRepository;
import dev.dentron.filestorage.application.port.out.FileObjectRepository;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort;
import dev.dentron.filestorage.common.util.DownloadTokenUtils;
import dev.dentron.filestorage.domain.DownloadToken;
import dev.dentron.filestorage.domain.FileObject;
import dev.dentron.filestorage.domain.exception.FileNotAccessibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueDownloadServiceTest {
    @Mock
    private DownloadTokenRepository tokenRepository;

    @Mock
    private ObjectStoragePort storage;

    @Mock
    private FileObjectRepository fileRepository;

    private DownloadTokenUtils tokenUtils;
    private IssueDownloadService service;

    @BeforeEach
    void setUp() {
        tokenUtils = new DownloadTokenUtils("pepper");
        service = new IssueDownloadService(
                tokenRepository,
                storage,
                tokenUtils,
                new FileAccessService(fileRepository),
                durations()
        );
    }

    @Test
    void presignGetUsesOwnedFileBucketAndKey() {
        UUID fileId = UUID.randomUUID();
        FileObject file = readyFile(fileId, "svc-a");
        when(fileRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(storage.presign(any())).thenReturn("https://example.test/download");

        Instant before = Instant.now();
        var result = service.presignGet(
                new NamespaceContext("svc-a"),
                new dev.dentron.filestorage.application.port.in.IssueDownloadUseCase.PresignedGetRequest(fileId)
        );
        Instant after = Instant.now();

        ArgumentCaptor<ObjectStoragePort.PresignedRequest> requestCaptor =
                ArgumentCaptor.forClass(ObjectStoragePort.PresignedRequest.class);
        verify(storage).presign(requestCaptor.capture());

        assertThat(requestCaptor.getValue().bucket()).isEqualTo(file.getBucket());
        assertThat(requestCaptor.getValue().objectKey()).isEqualTo(file.getObjectKey());
        assertThat(requestCaptor.getValue().method()).isEqualTo(ObjectStoragePort.PresignMethod.GET);
        assertThat(requestCaptor.getValue().query()).isEqualTo(Map.of());
        assertThat(requestCaptor.getValue().ttl()).isEqualTo(durations().presign().getTtl());
        assertThat(result.url()).isEqualTo("https://example.test/download");
        assertThat(result.expiresAt())
                .isAfterOrEqualTo(before.plus(durations().presign().getTtl()))
                .isBeforeOrEqualTo(after.plus(durations().presign().getTtl()));
    }

    @Test
    void presignGetRejectsFileThatIsNotReady() {
        UUID fileId = UUID.randomUUID();
        FileObject file = file(fileId, "svc-a", FileObject.Status.UPLOADED);
        when(fileRepository.findById(fileId)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> service.presignGet(
                new NamespaceContext("svc-a"),
                new dev.dentron.filestorage.application.port.in.IssueDownloadUseCase.PresignedGetRequest(fileId)
        ))
                .isInstanceOfSatisfying(FileNotAccessibleException.class, ex ->
                        assertThat(ex.reason()).isEqualTo(FileNotAccessibleException.Reason.NOT_READY)
                );
    }

    @Test
    void issueDownloadTokenSavesHashedToken() {
        UUID fileId = UUID.randomUUID();
        FileObject file = readyFile(fileId, "svc-a");
        when(fileRepository.findById(fileId)).thenReturn(Optional.of(file));

        Instant before = Instant.now();
        var result = service.issueDownloadToken(
                new NamespaceContext("svc-a"),
                new dev.dentron.filestorage.application.port.in.IssueDownloadUseCase.IssueTokenRequest("svc-b", fileId)
        );
        Instant after = Instant.now();

        ArgumentCaptor<DownloadToken> tokenCaptor = ArgumentCaptor.forClass(DownloadToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());

        assertThat(tokenCaptor.getValue().getFileId()).isEqualTo(fileId);
        assertThat(tokenCaptor.getValue().getIssuedByService()).isEqualTo("svc-a");
        assertThat(tokenCaptor.getValue().getAudienceService()).isEqualTo("svc-b");
        assertThat(tokenCaptor.getValue().getTokenHash()).isEqualTo(tokenUtils.hash(result.token()));
        assertThat(result.expiresAt())
                .isAfterOrEqualTo(before.plus(durations().token().ttl()))
                .isBeforeOrEqualTo(after.plus(durations().token().ttl()));
    }

    @Test
    void redeemTokenPresignsDownloadForValidToken() {
        UUID fileId = UUID.randomUUID();
        FileObject file = readyFile(fileId, "svc-a");
        String rawToken = "token-123";
        String hash = tokenUtils.hash(rawToken);
        DownloadToken token = DownloadToken.restore(
                1L,
                hash,
                fileId,
                "svc-a",
                "svc-b",
                Instant.now().plusSeconds(600),
                Instant.now(),
                "svc-b",
                Instant.now().minusSeconds(60),
                null
        );

        when(tokenRepository.tryRedeem(eq(hash), eq("svc-b"), any())).thenReturn(Optional.of(token));
        when(fileRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(storage.bucket()).thenReturn("bucket-main");
        when(storage.presign(any())).thenReturn("https://example.test/redeem");

        Instant before = Instant.now();
        var result = service.redeemToken(
                new NamespaceContext("svc-b"),
                new dev.dentron.filestorage.application.port.in.IssueDownloadUseCase.RedeemTokenRequest(rawToken)
        );
        Instant after = Instant.now();

        ArgumentCaptor<ObjectStoragePort.PresignedRequest> requestCaptor =
                ArgumentCaptor.forClass(ObjectStoragePort.PresignedRequest.class);
        verify(storage).presign(requestCaptor.capture());

        assertThat(requestCaptor.getValue().bucket()).isEqualTo("bucket-main");
        assertThat(requestCaptor.getValue().objectKey()).isEqualTo(file.getObjectKey());
        assertThat(requestCaptor.getValue().method()).isEqualTo(ObjectStoragePort.PresignMethod.GET);
        assertThat(requestCaptor.getValue().ttl()).isEqualTo(durations().token().presignTtl());
        assertThat(result.url()).isEqualTo("https://example.test/redeem");
        assertThat(result.expiresAt())
                .isAfterOrEqualTo(before.plus(durations().token().presignTtl()))
                .isBeforeOrEqualTo(after.plus(durations().token().presignTtl()));
    }

    private static FileObject readyFile(UUID fileId, String owner) {
        return file(fileId, owner, FileObject.Status.READY);
    }

    private static FileObject file(UUID fileId, String owner, FileObject.Status status) {
        return FileObject.restore(
                fileId,
                "bucket-main",
                owner,
                "uploads/" + fileId,
                "test.bin",
                128L,
                "sha-256",
                "etag-1",
                "application/octet-stream",
                status,
                Instant.now().minusSeconds(120),
                null
        );
    }

    private static DurationProperties durations() {
        return new DurationProperties(
                new DurationProperties.Token(Duration.ofHours(1), Duration.ofMinutes(15)),
                new DurationProperties.Presign(Duration.ofMinutes(10), Duration.ofMinutes(10)),
                new DurationProperties.Multipart(Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofMinutes(5))
        );
    }
}
