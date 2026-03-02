package dev.dentron.filestorage.api.exception;

import dev.dentron.filestorage.domain.exception.DownloadTokenAlreadyUsedException;
import dev.dentron.filestorage.domain.exception.DownloadTokenExpiredException;
import dev.dentron.filestorage.domain.exception.FileNotAccessibleException;
import dev.dentron.filestorage.domain.exception.UploadSessionException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.xml.bind.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class ApplicationExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle(ex.getTitleMessageCode());
        detail.setProperty("errors", ex.getFieldErrors().stream().map(
                error -> new ValidationFieldError(
                        error.getField(),
                        error.getRejectedValue(),
                        error.getDefaultMessage()
                )
        ));

        return createResponseEntity(detail, headers, HttpStatus.BAD_REQUEST, request);
    }

    private record ValidationFieldError(
            String field,
            Object rejectedValue,
            String message
    ) {}


    @ExceptionHandler(DownloadTokenAlreadyUsedException.class)
    public ProblemDetail handleDownloadTokenAlreadyUsedException(
            DownloadTokenAlreadyUsedException ex,
            HttpServletRequest request
    ) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setTitle("Download token already used");
        detail.setProperty("errCode", "DOWNLOAD_TOKEN_ALREADY_USED");
        enrichDetail(detail, request);

        return detail;
    }

    @ExceptionHandler(DownloadTokenExpiredException.class)
    public ProblemDetail handleDownloadTokenExpiredException(
            DownloadTokenExpiredException ex,
            HttpServletRequest request
    )  {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.GONE);
        detail.setTitle("Download token expired");
        detail.setProperty("errCode", "DOWNLOAD_TOKEN_EXPIRED");
        enrichDetail(detail, request);

        return detail;
    }

    @ExceptionHandler(FileNotAccessibleException.class)
    public ProblemDetail handleFileNotAccessibleException(
            FileNotAccessibleException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = switch (ex.reason()) {
            case NOT_READY -> HttpStatus.CONFLICT;
            case QUARANTINED -> HttpStatus.LOCKED;
            case DELETED -> HttpStatus.GONE;
        };

        ProblemDetail detail = ProblemDetail.forStatus(status);
        detail.setTitle("File is not accessible");
        detail.setProperty("errCode", "FILE_NOT_ACCESSIBLE");
        detail.setProperty("reason", ex.reason().name());
        detail.setProperty("status", ex.status().name());
        detail.setProperty("fileId", ex.fileId().toString());
        enrichDetail(detail, request);

        return detail;
    }

    @ExceptionHandler(UploadSessionException.class)
    public ProblemDetail handleUploadSessionException(
            UploadSessionException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = switch (ex.reason()) {
            case EXPIRED -> HttpStatus.GONE;
            case ALREADY_COMPLETED, INVALID_STATE -> HttpStatus.CONFLICT;
        };

        ProblemDetail detail = ProblemDetail.forStatus(status);
        detail.setTitle("Upload session is not valid");
        detail.setProperty("errCode", ex.code());
        detail.setProperty("reason", ex.reason().name());
        enrichDetail(detail, request);

        return detail;
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleEntityNotFoundException(
            EntityNotFoundException ex,
            HttpServletRequest request
    ) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setTitle("Resource not found");
        detail.setProperty("errCode", "NOT_FOUND");
        enrichDetail(detail, request);

        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Invalid request");
        detail.setProperty("errCode", "BAD_REQUEST");
        enrichDetail(detail, request);

        return detail;
    }

    private static void enrichDetail(ProblemDetail detail, HttpServletRequest request) {
        detail.setProperty("timestamp", Instant.now());

        if (request.getRequestId() != null) {
            detail.setProperty("requestId", request.getRequestId());
        }
    }

        @Override
        protected ResponseEntity<Object> createResponseEntity(
                @Nullable Object body,
                HttpHeaders headers,
                HttpStatusCode statusCode,
                WebRequest request) {

            if (body instanceof ProblemDetail pd) {
                pd.setProperty("timestamp", Instant.now());
            }

            if (request.getHeader("X-Request-ID") != null) {
                headers = HttpHeaders.copyOf(headers);
                headers.set("X-Request-ID", request.getHeader("X-Request-ID"));
            }

            return super.createResponseEntity(body, headers, statusCode, request);
        }
}

