package dev.dentron.filestorage.application.port.in;

import dev.dentron.filestorage.application.port.FilePart;
import dev.dentron.filestorage.application.port.NamespaceContext;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public interface CompleteUploadUseCase {

    CompletableFuture<CompleteUploadResult> completeMultipartUpload(NamespaceContext ns, CompleteUploadRequest request);

    record CompleteUploadRequest(
            String multipartUploadId,
            List<FilePart> parts
    ) {}

    record CompleteUploadResult(
            UUID fileId,
            String etag
    ) {}
}
