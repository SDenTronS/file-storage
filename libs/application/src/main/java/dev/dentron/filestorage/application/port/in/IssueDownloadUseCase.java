package dev.dentron.filestorage.application.port.in;

import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.PresignedUrl;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public interface IssueDownloadUseCase {

    PresignedUrl presignGet(NamespaceContext ns, PresignedGetRequest request);

    record PresignedGetRequest (
            UUID fileId
    ) {}

    PresignedUrl redeemToken(NamespaceContext redeemer, RedeemTokenRequest request);

    record RedeemTokenRequest(
            String token
    ) {}


    DownloadTokenResponse issueDownloadToken(NamespaceContext ns, IssueTokenRequest request);

    record IssueTokenRequest (
            String audienceService,
            UUID fileId
    ) {}

    record DownloadTokenResponse(
            String token,
            Instant expiresAt
    ) {}



}
