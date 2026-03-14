package dev.dentron.filestorage.api.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(
        name = "DirectUploadRequest",
        description = "Metadata for a direct small-file upload."
)
public record DirectUploadRequestDTO(
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
        String path
) {}
