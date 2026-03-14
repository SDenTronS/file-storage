package dev.dentron.filestorage.storages3;

import dev.dentron.filestorage.application.port.FilePart;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
@Component
public class MinIOObjectStorageAdapter implements ObjectStoragePort {
    private final MinioAsyncClient minioClient;
    private final MinIOCredentials credentials;
    private final MinIOExternalErrorMapper errorMapper;

    @Override
    public CompletableFuture<Boolean> exists(String bucket, String objectKey) {
        return executeAsync(
                bucket,
                objectKey,
                null,
                "statObject",
                () -> minioClient.statObject(StatObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectKey)
                                .build())
                        .thenApply(stat -> true),
                throwable -> errorMapper.isObjectMissing(throwable)
                        ? Optional.of(false)
                        : Optional.empty()
        );
    }

    @Override
    public PresignedPost presignPost(PresignedPostRequest request) {
        return executeSync(request.bucket(), request.objectKey(), null, "presignPost", () -> {
            log.debug("Retrieving post policy {}", request.objectKey());
            ZonedDateTime expiration = ZonedDateTime.now(ZoneId.systemDefault())
                    .plus(request.ttl());
            PostPolicy postPolicy = new PostPolicy(request.bucket(), expiration);
            postPolicy.addContentLengthRangeCondition(0, request.maxBytes());
            postPolicy.addStartsWithCondition(HttpHeaders.CONTENT_TYPE, request.contentType());

            URI url = URI.create(credentials.getEndpoint() + "/" + request.bucket());

            return new PresignedPost(url, minioClient.getPresignedPostFormData(postPolicy));
        });
    }

    @Override
    public String presign(PresignedRequest request) {
        Method method = switch (request.method()) {
            case GET -> Method.GET;
            case PUT -> Method.PUT;
            case HEAD -> Method.HEAD;
            case DELETE -> Method.DELETE;
        };

        return executeSync(request.bucket(), request.objectKey(), null, "presignObjectUrl", () -> {
            int expirySeconds = Math.toIntExact(request.ttl().getSeconds());

            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(request.bucket())
                            .method(method)
                            .extraQueryParams(request.query() == null ? Map.of() : request.query())
                            .object(request.objectKey())
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build());
        });
    }

    @Override
    public CompletableFuture<MultipartUpload> createMultipartUploadAsync(CreateMultipartUploadRequest request) {
        return executeAsync(
                request.bucket(),
                request.objectKey(),
                null,
                "createMultipartUpload",
                () -> minioClient.createMultipartUploadAsync(
                                request.bucket(),
                                credentials.getRegion(),
                                request.objectKey(),
                                null, null
                        )
                        .thenApply((multipartUpload) -> new MultipartUpload(multipartUpload.result().uploadId()))
        );
    }

    @Override
    public CompletableFuture<PutObjectResult> putObjectAsync(PutObjectRequest request) {
        return executeAsync(
                request.bucket(),
                request.objectKey(),
                null,
                "putObject",
                () -> minioClient.putObject(PutObjectArgs.builder()
                                .bucket(request.bucket())
                                .object(request.objectKey())
                                .contentType(request.contentType())
                                .stream(request.inputStream(), request.size(), -1)
                                .build())
                        .thenApply(response -> new PutObjectResult(response.etag()))
        );
    }

    @Override
    public CompletableFuture<String> completeMultipartUploadAsync(CompleteMultipartRequest request) {
        return executeAsync(
                request.bucket(),
                request.objectKey(),
                request.uploadId(),
                "completeMultipartUpload",
                () -> minioClient.completeMultipartUploadAsync(
                                request.bucket(),
                                credentials.getRegion(),
                                request.objectKey(),
                                request.uploadId(),
                                request.parts()
                                        .stream()
                                        .sorted((Comparator.comparingInt(FilePart::partNumber)))
                                        .map((filePart -> new Part(filePart.partNumber(), filePart.etag())))
                                        .toArray(Part[]::new),
                                null, null
                        )
                        .thenApply(GenericUploadResponse::etag)
        );
    }

    @Override
    public CompletableFuture<AbortResult> abortMultipartUploadAsync(AbortMultipartUploadRequest request) {
        return executeAsync(
                request.bucket(),
                request.objectKey(),
                request.uploadId(),
                "abortMultipartUpload",
                () -> minioClient.abortMultipartUploadAsync(
                                request.bucket(),
                                credentials.getRegion(),
                                request.objectKey(),
                                request.uploadId(),
                                null, null
                        )
                        .thenApply((r) -> AbortResult.ABORTED),
                throwable -> errorMapper.isAbortAlreadyGone(throwable)
                        ? Optional.of(AbortResult.ALREADY_GONE)
                        : Optional.empty()
        );
    }

    @Override
    public CompletableFuture<StorageObject> getObject(GetObjectRequest request) {
        return executeAsync(
                request.bucket(),
                request.objectKey(),
                null,
                "getObject",
                () -> minioClient
                        .getObject(GetObjectArgs.builder()
                                .bucket(request.bucket())
                                .object(request.objectKey())
                                .length(request.length())
                                .offset(request.offset())
                                .build())
                        .thenApply(r -> new StorageObject(r, request.length()))
        );
    }

    @Override
    public CompletableFuture<ObjectMetadata> getObjectMetadata(GetObjectMetadataRequest request) {
        return executeAsync(
                request.bucket(),
                request.objectKey(),
                null,
                "getObjectMetadata",
                () -> minioClient.statObject(StatObjectArgs.builder()
                                .bucket(request.bucket())
                                .object(request.objectKey())
                                .build())
                        .thenApply(r -> new ObjectMetadata(r.size(), r.contentType()))
        );
    }

    @Override
    public CompletableFuture<DeleteResult> deleteObjectAsync(DeleteObjectRequest request) {
        return executeAsync(
                request.bucket(),
                request.objectKey(),
                null,
                "removeObject",
                () -> minioClient.removeObject(RemoveObjectArgs.builder()
                                .bucket(request.bucket())
                                .object(request.objectKey())
                                .build())
                        .thenApply(r -> DeleteResult.DELETED),
                throwable -> errorMapper.isObjectMissing(throwable)
                        ? Optional.of(DeleteResult.ALREADY_GONE)
                        : Optional.empty()
        );
    }


    @Override
    public String bucket() {
        return credentials.getBucket();
    }

    @Override
    public String region() {
        return credentials.getRegion();
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T supply() throws Exception;
    }

    private <T> T executeSync(
            String bucket,
            String objectKey,
            String uploadId,
            String operation,
            ThrowingSupplier<T> supplier
    ) {
        try {
            return supplier.supply();
        } catch (Exception e) {
            log.error("Failed storage operation {}", operation, e);
            throw errorMapper.map(
                    e,
                    operation,
                    bucket,
                    objectKey,
                    uploadId);
        }
    }

    private <T> CompletableFuture<T> executeAsync(
            String bucket,
            String objectKey,
            String uploadId,
            String operation,
            ThrowingSupplier<CompletableFuture<T>> supplier
    ) {
        return executeAsync(
                bucket,
                objectKey,
                uploadId,
                operation,
                supplier,
                throwable -> Optional.empty()
        );
    }

    private <T> CompletableFuture<T> executeAsync(
            String bucket,
            String objectKey,
            String uploadId,
            String operation,
            ThrowingSupplier<CompletableFuture<T>> supplier,
            Function<Throwable, Optional<T>> recover
    ) {
        try {
            return supplier
                    .supply()
                    .exceptionally(throwable -> {
                        Optional<T> recovered = recover.apply(throwable);
                        log.error("Failed storage operation {}, recovered={}", operation, recovered.orElse(null), throwable);
                        if (recovered.isPresent()) {
                            return recovered.get();
                        }

                        throw new CompletionException(errorMapper.map(
                                throwable,
                                operation,
                                bucket,
                                objectKey,
                                uploadId));
                    });
        } catch (Exception e) {
            log.error("Failed storage operation {}", operation, e);
            return CompletableFuture.failedFuture(errorMapper.map(
                    e,
                    operation,
                    bucket,
                    objectKey,
                    uploadId)
            );
        }
    }

}
