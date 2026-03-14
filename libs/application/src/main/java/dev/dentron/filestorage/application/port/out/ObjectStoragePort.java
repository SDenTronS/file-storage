package dev.dentron.filestorage.application.port.out;

import dev.dentron.filestorage.application.port.FilePart;
import lombok.AllArgsConstructor;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface ObjectStoragePort {
    CompletableFuture<Boolean> exists(String bucket, String objectKey);

    PresignedPost presignPost(PresignedPostRequest request);

    record PresignedPostRequest (
            String bucket,
            String objectKey,
            String contentType,
            long maxBytes,
            Duration ttl
    ) {}

    record PresignedPost (
            URI url,
            Map<String, String> formData
    ) {}

    String presign(PresignedRequest request);

    enum PresignMethod {
        GET,
        PUT,
        HEAD,
        DELETE
    }

    record PresignedRequest (
            String bucket,
            String objectKey,
            PresignMethod method,
            Map<String, String> query,
            Duration ttl
    ) {}

    CompletableFuture<MultipartUpload> createMultipartUploadAsync(CreateMultipartUploadRequest request);

    record MultipartUpload (
            String uploadId
    ) {}

    CompletableFuture<PutObjectResult> putObjectAsync(PutObjectRequest request);

    record PutObjectRequest(
            String bucket,
            String objectKey,
            InputStream inputStream,
            long size,
            String contentType
    ) {}

    record PutObjectResult(
            String etag
    ) {}

    record CreateMultipartUploadRequest(
            String bucket,
            String region,
            String objectKey
    ) {}

    CompletableFuture<String> completeMultipartUploadAsync(CompleteMultipartRequest request);

    record CompleteMultipartRequest (
            String bucket,
            String objectKey,
            String uploadId,
            List<FilePart> parts
    )  {}


    CompletableFuture<AbortResult> abortMultipartUploadAsync(AbortMultipartUploadRequest request);

    enum AbortResult {
        ABORTED,
        ALREADY_GONE
    }

    record AbortMultipartUploadRequest (
            String bucket,
            String objectKey,
            String uploadId
    ) {}

    CompletableFuture<StorageObject> getObject(GetObjectRequest request);

    record StorageObject(InputStream in, long length) implements AutoCloseable {
            @Override
            public void close() throws Exception {
                in.close();
            }
        }

    record GetObjectRequest (
            String bucket,
            String objectKey,
            long offset,
            long length
    ) {}

    CompletableFuture<ObjectMetadata> getObjectMetadata(GetObjectMetadataRequest request);

    record ObjectMetadata (
            long size,
            String contentType
    ) {}

    record GetObjectMetadataRequest (String bucket, String objectKey) {}

    CompletableFuture<DeleteResult> deleteObjectAsync(DeleteObjectRequest request);

    enum DeleteResult {
        DELETED,
        ALREADY_GONE
    }

    record DeleteObjectRequest (String bucket, String objectKey) {}

    String bucket();

    String region();

}
