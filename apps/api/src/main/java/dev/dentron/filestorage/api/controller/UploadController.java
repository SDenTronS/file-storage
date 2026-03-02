package dev.dentron.filestorage.api.controller;

import dev.dentron.filestorage.api.dto.upload.*;
import dev.dentron.filestorage.api.security.CurrentService;
import dev.dentron.filestorage.api.security.ServiceDetails;
import dev.dentron.filestorage.application.port.FilePart;
import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.in.CompleteUploadUseCase;
import dev.dentron.filestorage.application.port.in.CreateUploadUseCase;
import dev.dentron.filestorage.application.port.in.DeleteFileUseCase;
import dev.dentron.filestorage.application.port.in.FileQueryUseCase;
import dev.dentron.filestorage.application.port.in.IssueDownloadUseCase;
import dev.dentron.filestorage.domain.FileObject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor

@RestController
@RequestMapping("/v1/")
public class UploadController {
    private final CompleteUploadUseCase completeUploadUseCase;
    private final CreateUploadUseCase createUploadUseCase;
    private final DeleteFileUseCase deleteFileUseCase;
    private final FileQueryUseCase fileQueryUseCase;
    private final IssueDownloadUseCase issueDownloadUseCase;


    @PostMapping("/uploads")
    public CompletableFuture<ResponseEntity<?>> createMultipartUpload(
            @RequestBody @Valid UploadCreateRequestDTO request,
            @CurrentService ServiceDetails currentService)
    {
        NamespaceContext ns = new NamespaceContext(currentService.serviceId());

        String safePath;

//        try {
//            safePath = PathUtils.sanitizePath(request.path());
//        } catch (Exception e) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid path");
//        }
        safePath = request.path();

        String safeFileName = StringUtils.getFilename(request.fileName());

        var createRequest = new CreateUploadUseCase.CreateUploadRequest(
                safePath,
                safeFileName,
                request.contentType(),
                request.overwrite(),
                request.size()
        );


        return createUploadUseCase.createMultipartUploadSessionAsync(ns, createRequest)
                .thenApply(result -> ResponseEntity.ok(
                        new UploadCreateResponseDTO(
                                result.id(),
                                result.multipartUploadId(),
                                result.fileId(),
                                result.expiresAt()
                        )));
    }

    @PostMapping("/uploads/{uploadId}")
    public ResponseEntity<?> uploadMultipart(
            @RequestParam("partNumber") @Positive int partNumber,
            @PathVariable @NotBlank String uploadId,
            @CurrentService ServiceDetails currentService
    ) {
        NamespaceContext ns = new NamespaceContext(currentService.serviceId());

        var presignedMultipartRequest = new CreateUploadUseCase.PresignedMultipartPutRequest(
                uploadId,
                partNumber
        );

        var presignedUrl = createUploadUseCase.presignMultipartPut(ns, presignedMultipartRequest);


        return ResponseEntity.ok(new PresignedUrlResponseDTO(
                presignedUrl.url(),
                presignedUrl.expiresAt()
        ));
    }

    @PostMapping("/uploads/{uploadId}/complete")
    public CompletableFuture<ResponseEntity<?>> completeUpload(
            @PathVariable String uploadId,
            @RequestBody @Valid UploadCompleteRequestDTO request,
            @CurrentService ServiceDetails currentService
    ) {
        NamespaceContext ns = new NamespaceContext(currentService.serviceId());
        List<FilePart> parts = request.parts().stream()
                .map(part -> new FilePart(part.etag(), part.partNumber()))
                .toList();

        var completeRequest = new CompleteUploadUseCase.CompleteUploadRequest(
                uploadId,
                parts
        );

        return completeUploadUseCase.completeMultipartUpload(ns, completeRequest)
                .thenApply(result -> ResponseEntity.ok(
                        new UploadCompleteResponseDTO(
                                result.fileId(),
                                result.etag()
                        )));


    }

    @PostMapping("/files/{fileId}/download-url")
    public ResponseEntity<?> presignFile(
            @PathVariable UUID fileId,
            @CurrentService ServiceDetails currentService) {
        NamespaceContext ns = new NamespaceContext(currentService.serviceId());

        var request = new IssueDownloadUseCase.PresignedGetRequest(fileId);
        var result = issueDownloadUseCase.presignGet(ns, request);


        return ResponseEntity.ok(
                new PresignedUrlResponseDTO(
                        result.url(),
                        result.expiresAt()
                ));
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<FileMetadataResponseDTO> getFile(
            @PathVariable UUID fileId,
            @CurrentService ServiceDetails currentService
    ) {
        NamespaceContext ns = new NamespaceContext(currentService.serviceId());
        var request = new FileQueryUseCase.GetFileRequest(fileId);
        var file = fileQueryUseCase.getFile(ns, request);

        if (file.status() == FileObject.Status.DELETED) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new FileMetadataResponseDTO(
                file.fileId(),
                file.originalName(),
                file.size(),
                file.contentType(),
                toResponseStatus(file.status()),
                file.createdAt()
        ));
    }

    @PostMapping("/download-tokens/redeem")
    public ResponseEntity<?> redeemToken(
            @RequestParam("token") String token,
            @CurrentService ServiceDetails currentService
    ) {
        NamespaceContext ns = new NamespaceContext(currentService.serviceId());

        var request = new IssueDownloadUseCase.RedeemTokenRequest(token);
        var result = issueDownloadUseCase.redeemToken(ns, request);

        return ResponseEntity.ok(
                new PresignedUrlResponseDTO(
                        result.url(),
                        result.expiresAt()
                ));
    }

    @PostMapping("/files/{fileId}/download-token")
    public ResponseEntity<?> issueDownloadToken(
            @PathVariable UUID fileId,
            @RequestBody @Valid IssueDownloadTokenRequestDTO request,
            @CurrentService ServiceDetails currentService)
    {
        NamespaceContext ns = new NamespaceContext(currentService.serviceId());

        var tokenRequest = new IssueDownloadUseCase.IssueTokenRequest(
                request.audienceService(),
                fileId
        );
        var result = issueDownloadUseCase.issueDownloadToken(ns, tokenRequest);

        return ResponseEntity.ok(
                new DownloadTokenResponseDTO(
                        result.token(),
                        result.expiresAt()
                ));
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable UUID fileId,
            @CurrentService ServiceDetails currentService)
    {
        NamespaceContext ns = new NamespaceContext(currentService.serviceId());

        var request = new DeleteFileUseCase.DeleteFileRequest(fileId);
        deleteFileUseCase.deleteFile(ns, request);

        return ResponseEntity.noContent().build();
    }

    private FileStatusResponse toResponseStatus(FileObject.Status status) {
        return switch (status) {
            case UPLOADING -> FileStatusResponse.UPLOADING;
            case UPLOADED -> FileStatusResponse.UPLOADED;
            case READY -> FileStatusResponse.READY;
            case QUARANTINED -> FileStatusResponse.QUARANTINED;
            case REJECTED -> FileStatusResponse.REJECTED;
            case DELETED -> FileStatusResponse.DELETED;
        };
    }

}
