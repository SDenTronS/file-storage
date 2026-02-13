package dev.dentron.filestorage.application.port.in;

import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.PresignedUrl;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface CreateUploadUseCase {

    CompletableFuture<CreateUploadResult> createMultipartUploadSessionAsync(NamespaceContext ns, CreateUploadRequest request);

    record CreateUploadRequest(
            String prefix,
            String originalFileName,
            String expectedContentType,
            boolean override,
            long sizeBytes
    ) {}

    record CreateUploadResult(
            UUID id,
            String multipartUploadId,
            UUID fileId,
            Instant expiresAt
    ) {}

    PresignedUrl presignMultipartPut(NamespaceContext ns, PresignedMultipartPutRequest request);

    record PresignedMultipartPutRequest(
            String multipartUploadId,
            int partNumber
    ) {}

    PresignedUrl presignPut(NamespaceContext ns, PresignedPutRequest request);

    record PresignedPutRequest(
            UUID sessionId
    ) {}
}
