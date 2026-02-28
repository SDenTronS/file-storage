package dev.dentron.filestorage.api.dto.upload;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(
        name = "UploadCompleteRequest",
        description = "Request payload used to finalize a multipart upload with the uploaded part metadata."
)
public record UploadCompleteRequestDTO(
        @NotEmpty
        @Valid
        @ArraySchema(
                arraySchema = @Schema(
                        description = "Uploaded parts that should be assembled into the final file, in multipart upload order.",
                        requiredMode = Schema.RequiredMode.REQUIRED
                ),
                schema = @Schema(implementation = UploadPartDTO.class)
        )
        List<UploadPartDTO> parts
) {}
