package dev.dentron.filestorage.api.controller;

import dev.dentron.filestorage.api.dto.upload.*;
import dev.dentron.filestorage.api.security.CurrentService;
import dev.dentron.filestorage.api.security.ServiceDetails;
import dev.dentron.filestorage.application.port.FilePart;
import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.in.AbortUploadUseCase;
import dev.dentron.filestorage.application.port.in.CompleteUploadUseCase;
import dev.dentron.filestorage.application.port.in.CreateUploadUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor

@RestController
@Tag(name = "Uploads")
@RequestMapping("/v1/")
public class UploadController {
    private final AbortUploadUseCase abortUploadUseCase;
    private final CompleteUploadUseCase completeUploadUseCase;
    private final CreateUploadUseCase createUploadUseCase;


    @Operation(summary = "Create upload session", description = "Start multipart upload.")
    @ApiResponse(responseCode = "200", description = "Upload session created")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "409", description = "File already exists")
    @ApiResponse(responseCode = "502", description = "Storage error")
    @PostMapping(value = "/uploads", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<UploadCreateResponseDTO>> createMultipartUpload(
            @RequestBody @Valid UploadCreateRequestDTO request,
            @Parameter(hidden = true)
            @CurrentService ServiceDetails currentService)
    {
        NamespaceContext ns = new NamespaceContext(currentService.serviceId());
        String safePath = normalizePath(request.path());
        String safeFileName = normalizeFileName(request.fileName());

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

    @Operation(summary = "Direct upload", description = "Upload file in one request.")
    @ApiResponse(responseCode = "200", description = "File uploaded")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "409", description = "File already exists")
    @ApiResponse(responseCode = "502", description = "Storage error")
    @PostMapping(value = "/uploads/direct", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<UploadCompleteResponseDTO>> directUpload(
            @RequestPart("request") @Valid DirectUploadRequestDTO request,
            @RequestPart("file") MultipartFile file,
            @Parameter(hidden = true)
            @CurrentService ServiceDetails currentService
    ) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty.".intern());
        }

        NamespaceContext ns = new NamespaceContext(currentService.serviceId());
        String safePath = normalizePath(request.path());
        String safeFileName = normalizeFileName(file.getOriginalFilename());
        String contentType = file.getContentType();

        var directUploadRequest = new CreateUploadUseCase.DirectUploadRequest(
                safePath,
                safeFileName,
                contentType,
                file.getSize(),
                file::getInputStream
        );

        return createUploadUseCase
                .uploadFile(ns, directUploadRequest)
                .thenApply(result -> ResponseEntity.ok(
                        new UploadCompleteResponseDTO(result.fileId(), result.etag()))
                );

    }

    @Operation(summary = "Presign upload part", description = "Get upload URL for part.")
    @ApiResponse(responseCode = "200", description = "URL generated")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "404", description = "Upload not found")
    @ApiResponse(responseCode = "409", description = "Upload state conflict")
    @ApiResponse(responseCode = "410", description = "Upload expired")
    @PostMapping("/uploads/{uploadId}")
    public ResponseEntity<PresignedUrlResponseDTO> uploadMultipart(
            @RequestParam("partNumber") @Positive int partNumber,
            @PathVariable @NotBlank String uploadId,
            @Parameter(hidden = true)
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

    @Operation(summary = "Abort upload", description = "Cancel multipart upload.")
    @ApiResponse(responseCode = "204", description = "Upload aborted")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "404", description = "Upload not found")
    @ApiResponse(responseCode = "409", description = "Upload state conflict")
    @DeleteMapping("/uploads/{sessionId}")
    public ResponseEntity<Void> abortUpload(
            @PathVariable UUID sessionId,
            @Parameter(hidden = true)
            @CurrentService ServiceDetails currentService
    ) {
        NamespaceContext ns = new NamespaceContext(currentService.serviceId());
        var request = new AbortUploadUseCase.AbortUploadRequest(sessionId);
        abortUploadUseCase.abortUpload(ns, request);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Complete upload", description = "Finish multipart upload.")
    @ApiResponse(responseCode = "200", description = "Upload completed")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "404", description = "Upload not found")
    @ApiResponse(responseCode = "409", description = "Upload state conflict")
    @ApiResponse(responseCode = "410", description = "Upload expired")
    @ApiResponse(responseCode = "422", description = "Invalid upload parts")
    @ApiResponse(responseCode = "502", description = "Storage error")
    @PostMapping("/uploads/{uploadId}/complete")
    public CompletableFuture<ResponseEntity<UploadCompleteResponseDTO>> completeUpload(
            @PathVariable String uploadId,
            @RequestBody @Valid UploadCompleteRequestDTO request,
            @Parameter(hidden = true)
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

    private String normalizePath(String path) {
        return StringUtils.hasText(path) ? path : "";
    }

    private String normalizeFileName(String originalFileName) {
        String safeFileName = StringUtils.getFilename(originalFileName);
        if (!StringUtils.hasText(safeFileName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File name is required.");
        }

        return safeFileName;
    }
}
