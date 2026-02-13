package dev.dentron.filestorage.api.dto.upload;

import jakarta.validation.constraints.NotNull;

public record IssueDownloadTokenRequestDTO(
        @NotNull String audienceService
) {}
