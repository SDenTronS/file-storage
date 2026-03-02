package dev.dentron.filestorage.api.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(
        name = "UploadCreateRequest",
        description = "Request for creating a multipart upload session."
)
public record UploadCreateRequestDTO(
        @Schema(
                description = "Optional relative storage path. Use an empty value to upload into the root. "
                        + "The path must consist of slash-separated segments, for example /documents/2026. "
                        + "Each segment must be 1 to 64 characters long, start with a lowercase letter or digit, "
                        + "and may contain lowercase letters, digits, underscores, or hyphens. "
                        + "The total path length must not exceed 256 characters.",
                example = "/documents/2026"
        )
        @Pattern(
                regexp = "^(/[a-z0-9][a-z0-9_-]{0,63})*$",
                message = "Path must be empty or contain slash-separated segments where each segment is 1 to 64 characters long and uses lowercase letters, digits, underscores, or hyphens."
        )
        @Size(max = 256, message = "Path must not exceed 256 characters.")
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


        @Positive(message = "File size must be greater than zero.")
        @Schema(
                description = "File size in bytes. The value must be greater than zero.",
                example = "1048576",
                minimum = "1",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long size) {
}
