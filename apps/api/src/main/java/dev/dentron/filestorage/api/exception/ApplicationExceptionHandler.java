package dev.dentron.filestorage.api.exception;

import dev.dentron.filestorage.domain.exception.DownloadTokenAlreadyUsedException;
import dev.dentron.filestorage.domain.exception.DownloadTokenExpiredException;
import dev.dentron.filestorage.domain.exception.FileNotAccessibleException;
import dev.dentron.filestorage.domain.exception.UploadSessionException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static dev.dentron.filestorage.common.util.ExceptionUtils.unwrap;

@Slf4j
@RestControllerAdvice
public class ApplicationExceptionHandler {
    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<ApiErrorResponse> handleCompletionException(
            CompletionException ex,
            HttpServletRequest request
    ) {
        return mapCause(unwrap(ex), request);
    }

    @ExceptionHandler(ExecutionException.class)
    public ResponseEntity<ApiErrorResponse> handleExecutionException(
            ExecutionException ex,
            HttpServletRequest request
    ) {
        return mapCause(unwrap(ex), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request
    ) {
        ApiErrorResponse body = ApiErrorResponse.fromResponseStatusException(ex, request.getRequestId());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntimeException(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        return mapCause(ex, request);
    }

    private ResponseEntity<ApiErrorResponse> mapCause(Throwable cause, HttpServletRequest request) {

        if (cause instanceof DownloadTokenAlreadyUsedException) {
            return clientError(
                    HttpStatus.CONFLICT,
                    "Conflict",
                    request,
                    cause,
                    List.of(fieldError("code", "DOWNLOAD_TOKEN_ALREADY_USED"))
            );
        }

        if (cause instanceof DownloadTokenExpiredException) {
            return clientError(
                    HttpStatus.GONE,
                    "Resource is no longer available",
                    request,
                    cause,
                    List.of(fieldError("code", "DOWNLOAD_TOKEN_EXPIRED"))
            );
        }

        if (cause instanceof FileNotAccessibleException ex) {
            HttpStatus status = switch (ex.reason()) {
                case NOT_READY -> HttpStatus.CONFLICT;
                case QUARANTINED -> HttpStatus.LOCKED;
                case DELETED -> HttpStatus.GONE;
            };

            List<ApiErrorResponse.FieldError> details = new ArrayList<>();
            details.add(fieldError("code", "FILE_NOT_ACCESSIBLE"));
            details.add(fieldError("reason", ex.reason().name()));
            details.add(fieldError("status", ex.status().name()));
            details.add(fieldError("fileId", ex.fileId().toString()));

            return clientError(status, "File is not accessible", request, cause, details);
        }

        if (cause instanceof UploadSessionException ex) {
            HttpStatus status = switch (ex.reason()) {
                case EXPIRED -> HttpStatus.GONE;
                case ALREADY_COMPLETED, INVALID_STATE -> HttpStatus.CONFLICT;
            };

            List<ApiErrorResponse.FieldError> details = new ArrayList<>();
            details.add(fieldError("code", ex.code()));
            details.add(fieldError("reason", ex.reason().name()));

            return clientError(status, "Upload session is not valid", request, cause, details);
        }

        if (cause instanceof EntityNotFoundException) {
            return clientError(
                    HttpStatus.NOT_FOUND,
                    "Resource not found",
                    request,
                    cause,
                    List.of(fieldError("code", "NOT_FOUND"))
            );
        }

        if (cause instanceof IllegalArgumentException) {
            return clientError(
                    HttpStatus.BAD_REQUEST,
                    "Invalid request",
                    request,
                    cause,
                    List.of(fieldError("code", "BAD_REQUEST"))
            );
        }

        return serverError(request, cause);
    }

    private ResponseEntity<ApiErrorResponse> clientError(
            HttpStatus status,
            String safeMessage,
            HttpServletRequest request,
            Throwable t,
            List<ApiErrorResponse.FieldError> fieldErrors
    ) {
        log.warn("Request failed: status={}, requestId={}", status.value(), request.getRequestId(), t);

        ApiErrorResponse body = ApiErrorResponse.of(safeMessage, request.getRequestId(), fieldErrors, null);
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<ApiErrorResponse> serverError(HttpServletRequest request, Throwable t) {
        log.warn("Unhandled error: requestId={}", request.getRequestId(), t);
        ApiErrorResponse body = ApiErrorResponse.internalError(request.getRequestId());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private static ApiErrorResponse.FieldError fieldError(String field, String message) {
        return ApiErrorResponse.FieldError.builder()
                .field(field)
                .message(message)
                .build();
    }
}

