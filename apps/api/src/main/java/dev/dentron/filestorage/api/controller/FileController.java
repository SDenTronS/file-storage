package dev.dentron.filestorage.api.controller;

import dev.dentron.filestorage.api.dto.upload.FileMetadataPageResponseDTO;
import dev.dentron.filestorage.api.dto.upload.FileMetadataResponseDTO;
import dev.dentron.filestorage.api.dto.upload.FileStatusResponse;
import dev.dentron.filestorage.api.security.CurrentService;
import dev.dentron.filestorage.api.security.ServiceDetails;
import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.in.DeleteFileUseCase;
import dev.dentron.filestorage.application.port.in.FileQueryUseCase;
import dev.dentron.filestorage.domain.FileObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@Tag(name = "Files")
@RequestMapping("/v1/")
public class FileController {
    private final DeleteFileUseCase deleteFileUseCase;
    private final FileQueryUseCase fileQueryUseCase;

    @Operation(summary = "List files", description = "Get file list.")
    @ApiResponse(responseCode = "200", description = "Files returned")
    @GetMapping("/files")
    public ResponseEntity<FileMetadataPageResponseDTO> getFiles(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @Parameter(hidden = true)
            @CurrentService ServiceDetails currentService
    ) {
        NamespaceContext ns = new NamespaceContext(currentService.serviceId());
        var request = new FileQueryUseCase.ListFilesRequest(cursor, limit);
        var page = fileQueryUseCase.listFiles(ns, request);

        return ResponseEntity.ok(new FileMetadataPageResponseDTO(
                page.items().stream()
                        .map(this::toResponse)
                        .toList(),
                page.nextCursor()
        ));
    }

    @Operation(summary = "Get file", description = "Get file metadata.")
    @ApiResponse(responseCode = "200", description = "File returned")
    @ApiResponse(responseCode = "404", description = "File not found")
    @ApiResponse(responseCode = "409", description = "File not ready")
    @ApiResponse(responseCode = "410", description = "File deleted")
    @ApiResponse(responseCode = "423", description = "File quarantined")
    @GetMapping("/files/{fileId}")
    public ResponseEntity<FileMetadataResponseDTO> getFile(
            @PathVariable UUID fileId,
            @Parameter(hidden = true)
            @CurrentService ServiceDetails currentService
    ) {
        NamespaceContext ns = new NamespaceContext(currentService.serviceId());
        var request = new FileQueryUseCase.GetFileRequest(fileId);
        var file = fileQueryUseCase.getFile(ns, request);

        if (file.status() == FileObject.Status.DELETED) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(toResponse(file));
    }

    @Operation(summary = "Delete file", description = "Mark file as deleted.")
    @ApiResponse(responseCode = "204", description = "File deleted")
    @ApiResponse(responseCode = "404", description = "File not found")
    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable UUID fileId,
            @Parameter(hidden = true)
            @CurrentService ServiceDetails currentService
    ) {
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

    private FileMetadataResponseDTO toResponse(FileQueryUseCase.FileMetadata file) {
        return new FileMetadataResponseDTO(
                file.fileId(),
                file.originalName(),
                file.size(),
                file.contentType(),
                toResponseStatus(file.status()),
                file.createdAt()
        );
    }
}
