package dev.dentron.filestorage.api.dto.upload;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UploadPartDTO(
        @NotNull String etag,
        @Positive int partNumber
) {}
