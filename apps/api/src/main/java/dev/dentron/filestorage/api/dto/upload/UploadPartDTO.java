package dev.dentron.filestorage.api.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(
        name = "UploadPart",
        description = "Metadata for a single uploaded part used to finalize a multipart upload."
)
public record UploadPartDTO(
        @NotNull
        @Schema(
                description = "ETag returned by object storage after uploading a part.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String etag,


        @Positive
        @Schema(
                description = "Sequential multipart part number associated with the uploaded chunk.",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED,
                minimum = "1"
        )
        int partNumber
) {}
