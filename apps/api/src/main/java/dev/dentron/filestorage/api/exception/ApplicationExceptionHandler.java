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
//
//    @ExceptionHandler(HttpMessageNotReadableException.class)
//    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(
//            HttpMessageNotReadableException ex,
//            HttpServletRequest request
//    ) {
//        log.warn("Invalid request body for {}: {}", request.getRequestURI(), ex.getMessage());
//
//        String message = "Invalid request body";
//        String exceptionMessage = ex.getMessage();
//        if (exceptionMessage != null && exceptionMessage.startsWith("Required request body is missing")) {
//            message = "Request body is required";
//        }
//
//        ApiErrorResponse body = ApiErrorResponse.of(
//                message,
//                request.getRequestId()
//        );
//
//        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
//    }
//
//    @ExceptionHandler(MethodArgumentNotValidException.class)
//    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
//            MethodArgumentNotValidException ex,
//            HttpServletRequest request
//    ) {
//        log.warn("Validation failed for {}: {}", request.getRequestURI(), ex.getMessage());
//
//        List<ApiErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
//                .getFieldErrors()
//                .stream()
//                .map(error -> ApiErrorResponse.FieldError.builder()
//                        .field(error.getField())
//                        .message(error.getDefaultMessage())
//                        .rejectedValue(error.getRejectedValue())
//                        .build())
//                .toList();
//
//        ApiErrorResponse body = ApiErrorResponse.validationError(
//                "Validation failed",
//                request.getRequestId(),
//                fieldErrors
//        );
//
//        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
//    }
//
//    @ExceptionHandler(ResponseStatusException.class)
//    public ResponseEntity<ApiErrorResponse> handleResponseException(ResponseStatusException ex,
//                                                                    HttpServletRequest request) {
//        log.warn("Response status exception in controller", ex);
//
//        HttpStatusCode statusCode = ex.getStatusCode();
//        HttpStatus status = HttpStatus.valueOf(statusCode.value());
//
//        String requestId = request.getRequestId();
//
//        ApiErrorResponse body = ApiErrorResponse.fromResponseStatusException(ex, requestId);
//
//        return ResponseEntity.status(status).body(body);
//    }
//
//    @ExceptionHandler(EntityNotFoundException.class)
//    public ResponseEntity<ApiErrorResponse> handleEntityNotFound(
//            EntityNotFoundException ex,
//            HttpServletRequest request
//    ) {
//        log.warn("Entity not found for {}: {}", request.getRequestURI(), ex.getMessage());
//
//        String message = ex.getMessage();
//        if (message == null || message.isBlank()) {
//            message = "Resource not found";
//        }
//
//        ApiErrorResponse body = ApiErrorResponse.of(
//                message,
//                request.getRequestId()
//        );
//
//        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
//    }
//
//    @ExceptionHandler(AccessDeniedException.class)
//    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
//            AccessDeniedException ex,
//            HttpServletRequest request
//    ) {
//        log.warn("Access denied for {}: {}", request.getRequestURI(), ex.getMessage());
//
//        String message = ex.getMessage();
//        if (message == null || message.isBlank()) {
//            message = "Access denied";
//        }
//
//        ApiErrorResponse body = ApiErrorResponse.of(
//                message,
//                request.getRequestId()
//        );
//
//        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
//    }
//
//    @ExceptionHandler({
//            AuthenticationCredentialsNotFoundException.class,
//            UserPrincipalNotFoundException.class
//    })
//    public ResponseEntity<ApiErrorResponse> handleAuthenticationMissing(
//            Exception ex,
//            HttpServletRequest request
//    ) {
//        log.warn("Authentication missing for {}: {}", request.getRequestURI(), ex.getMessage());
//
//        String message = ex.getMessage();
//        if (message == null || message.isBlank()) {
//            message = "Authentication required";
//        }
//
//        ApiErrorResponse body = ApiErrorResponse.of(
//                message,
//                request.getRequestId()
//        );
//
//        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
//    }

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
            details.add(fieldError("code", ex.code()));            // UPLOAD_SESSION_*
            details.add(fieldError("reason", ex.reason().name()));
            details.add(fieldError("status", ex.status().name()));

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

