package dev.dentron.filestorage.api.dto.upload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UploadCompleteRequestDTO(
        @NotNull @Valid List<UploadPartDTO> parts
) {}
