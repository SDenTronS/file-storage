package dev.dentron.filestorage.api.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(
        name = "UploadCreateRequest",
        description = "Request for creating a multipart upload session."
)
public record UploadCreateRequestDTO(
        @Schema(
                description = "Optional relative storage path. Use an empty value to upload into the root.",
                example = "documents/2026"
        )
        String path,


        @NotNull
        @Schema(
                description = "Original file name as provided by the client.",
                example = "report.pdf",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String fileName,


        @Schema(
                description = "MIME type for the uploaded file.",
                example = "application/pdf"
        )
        String contentType,


        @Schema(
                description = "Whether an existing file with the same path and name may be replaced.",
                example = "false",
                deprecated = true
        )
        boolean overwrite,


        @Positive
        @Schema(
                description = "File size in bytes. Must be greater than zero.",
                example = "1048576",
                minimum = "1",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long size) {
}
