package dev.dentron.filestorage.api.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(
        name = "UploadCompleteResponse",
        description = "Response payload returned after successfully completing a multipart upload."
)
public record UploadCompleteResponseDTO(
        @Schema(
                description = "Identifier of the uploaded file that has been finalized and stored.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        UUID fileId,

        @Schema(
                description = "Final Etag returned by object storage for the completed file.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String etag
) {}
