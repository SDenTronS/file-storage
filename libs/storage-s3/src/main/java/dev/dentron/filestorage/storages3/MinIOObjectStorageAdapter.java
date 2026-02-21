package dev.dentron.filestorage.storages3;

import dev.dentron.filestorage.application.port.FilePart;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort;
import dev.dentron.filestorage.common.util.ExceptionUtils;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
@Component
public class MinIOObjectStorageAdapter implements ObjectStoragePort {
    private final MinioAsyncClient minioClient;
    private final MinIOCredentials credentials;

    @Override
    public CompletableFuture<Boolean> exists(String bucket, String objectKey) {
        try {
            return minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build())
                    .thenApply(stat -> true)
                    .exceptionally(throwable -> {
                        Throwable cause = ExceptionUtils.unwrap(throwable);

                        if (cause instanceof ErrorResponseException e) {
                            String code = e.errorResponse().code();

                            if (code.equals("NoSuchKey")) {
                                return false;
                            }
                        }

                        throw new CompletionException(cause);
                    });
        } catch (Exception e) {
            log.error("Failed to check object existence", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public PresignedPost presignPost(PresignedPostRequest request) {
        try {
            log.debug("Retrieving post policy {}", request.objectKey());
            ZonedDateTime expiration = ZonedDateTime.now(ZoneId.systemDefault())
                    .plus(request.ttl());
            PostPolicy postPolicy = new PostPolicy(request.bucket(), expiration);
            postPolicy.addContentLengthRangeCondition(0, request.maxBytes());
            postPolicy.addStartsWithCondition(HttpHeaders.CONTENT_TYPE, request.contentType());

            URI url = URI.create(credentials.getEndpoint() + "/" + request.bucket());

            return new PresignedPost(url, minioClient.getPresignedPostFormData(postPolicy));

        } catch (Exception e) {
            throw new RuntimeException("Failed to get presigned object url", e);
        }
    }

    @Override
    public String presign(PresignedRequest request) {
        Method method = switch (request.method()) {
            case GET -> Method.GET;
            case PUT -> Method.PUT;
            case HEAD -> Method.HEAD;
            case DELETE -> Method.DELETE;
        };

        try {
            int expirySeconds = Math.toIntExact(request.ttl().getSeconds());

            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(request.bucket())
                            .method(method)
                            .extraQueryParams(request.query() == null ? Map.of() : request.query())
                            .object(request.objectKey())
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get presigned object url", e);
        }
    }

    @Async
    @Override
    public CompletableFuture<MultipartUpload> createMultipartUploadAsync(CreateMultipartUploadRequest request) {
        try {
            return minioClient.createMultipartUploadAsync(
                    request.bucket(),
                    credentials.getRegion(),
                    request.objectKey(),
                    null, null
            ).thenApply((multipartUpload) -> new MultipartUpload(multipartUpload.result().uploadId()));
        } catch (Exception e) {
            log.error("Failed to create multipart upload", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<String> completeMultipartUploadAsync(CompleteMultipartRequest request) {
        try {
            return minioClient.completeMultipartUploadAsync(
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
            ).thenApply(GenericUploadResponse::etag);
        } catch (Exception e) {
            log.error("Failed to complete multipart upload", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<AbortResult> abortMultipartUploadAsync(AbortMultipartUploadRequest request) {
        try {
            return minioClient.abortMultipartUploadAsync(
                            request.bucket(),
                            credentials.getRegion(),
                            request.objectKey(),
                            request.uploadId(),
                            null, null
                    )
                    .thenApply((r) -> AbortResult.ABORTED)
                    .exceptionally(throwable -> {
                                Throwable cause = ExceptionUtils.unwrap(throwable);

                                if (cause instanceof ErrorResponseException e) {
                                    String code = e.errorResponse().code();

                                    if (code.equals("NoSuchUpload") || code.equals("NoSuchKey")) {
                                        return AbortResult.ALREADY_GONE;
                                    }
                                }

                                throw new CompletionException(cause);
                            }
                    );
        } catch (Exception e) {
            log.error("Failed to abort multipart upload", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<StorageObject> getObject(GetObjectRequest request) {
        try {
            return minioClient
                    .getObject(GetObjectArgs.builder()
                            .bucket(request.bucket())
                            .object(request.objectKey())
                            .length(request.length())
                            .offset(request.offset())
                            .build())
                    .thenApply(r -> new StorageObject(r, request.length()));
        } catch (Exception e) {
            log.error("Failed to get object", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<ObjectMetadata> getObjectMetadata(GetObjectMetadataRequest request) {
        try {
            return minioClient.statObject(StatObjectArgs.builder()
                            .bucket(request.bucket())
                            .object(request.objectKey())
                            .build())
                    .thenApply(r -> new ObjectMetadata(r.size(), r.contentType()));
        } catch (Exception e) {
            log.error("Failed to get object metadata", e);
            return CompletableFuture.failedFuture(e);
        }
    }


    @Override
    public String bucket() {
        return credentials.getBucket();
    }

    @Override
    public String region() {
        return credentials.getRegion();
    }

}
