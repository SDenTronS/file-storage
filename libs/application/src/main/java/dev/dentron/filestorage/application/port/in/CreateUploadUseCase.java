package dev.dentron.filestorage.application.port.in;

import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.PresignedUrl;
import org.apache.commons.lang3.function.FailableSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface CreateUploadUseCase {

    CompletableFuture<CreateUploadResult> createMultipartUploadSessionAsync(NamespaceContext ns, CreateUploadRequest request);

    CompletableFuture<DirectUploadResult> uploadFile(NamespaceContext ns, DirectUploadRequest request);

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

    record DirectUploadRequest(
            String prefix,
            String originalFileName,
            String expectedContentType,
            long sizeBytes,
            FailableSupplier<InputStream, IOException> inputStreamSupplier
    ) {}

    record DirectUploadResult(
            UUID fileId,
            String etag
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
