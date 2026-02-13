package dev.dentron.filestorage.api.dto.upload;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UploadCreateRequestDTO(
        String path,
        @NotNull String fileName,
        String contentType,
        boolean overwrite,
        @Positive long size) {
}
