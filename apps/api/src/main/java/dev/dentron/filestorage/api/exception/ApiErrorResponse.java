package dev.dentron.filestorage.api.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ApiErrorResponse {
    /**
     * Стандартизированный ответ об ошибке для REST API с метаданными запроса,
     * описанием ошибки, а также дополнительными данными (например, ошибками полей).
     * Используется глобальными обработчиками исключений для формирования единого формата ответа.
     */


    @JsonProperty(required = true)
    private Instant timestamp;
    private String requestId;

    @JsonProperty(required = true)
    private String message;
    private List<FieldError> fieldErrors;
    private Map<String, Object> extra;

    /**
     * Базовый ответ об ошибке с сообщением без метаданных.
     *
     * @param message описание для клиента
     * @return стандартный {@link ApiErrorResponse}
     */
    public static ApiErrorResponse of(
            String message
    ) {
        return ApiErrorResponse.of(message, null);
    }

    /**
     * Ответ об ошибке с сообщением и базовой метаинформацией запроса.
     *
     * @param message   описание ошибки
     * @param requestId идентификатор запроса/трейса
     * @return сформированный {@link ApiErrorResponse}
     */
    public static ApiErrorResponse of(
            String message,
            String requestId
    ) {
        return of(message, requestId, null, null);
    }

    /**
     * Ответ об ошибке с метаданными запроса и списком ошибок валидации.
     *
     * @param message     описание ошибки
     * @param requestId   идентификатор запроса
     * @param fieldErrors список ошибок по отдельным полям
     * @return сформированный {@link ApiErrorResponse}
     */
    public static ApiErrorResponse of(
            String message,
            String requestId,
            List<FieldError> fieldErrors
    ) {
        return of(message, requestId, fieldErrors, null);
    }

    /**
     * Ответ об ошибке с метаданными, ошибками валидации и дополнительными данными.
     * Дополнительные данные можно использовать для передачи технических подробностей.
     *
     * @param message     описание ошибки
     * @param requestId   идентификатор запроса
     * @param fieldErrors список ошибок по отдельным полям
     * @param extra       дополнительные данные об ошибке
     * @return сформированный {@link ApiErrorResponse}
     */
    public static ApiErrorResponse of(
            String message,
            String requestId,
            List<FieldError> fieldErrors,
            Map<String, Object> extra
    ) {
        return ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .requestId(requestId)
                .message(message)
                .fieldErrors(fieldErrors)
                .extra(extra)
                .build();
    }

    /**
     * Фабричный метод для ошибок валидации.
     */
    public static ApiErrorResponse validationError(
            String message,
            String requestId,
            List<FieldError> fieldErrors
    ) {
        return of(message, requestId, fieldErrors, null);
    }

    /**
     * Шаблон ответа для внутренних ошибок.
     *
     * @param requestId идентификатор запроса
     * @return стандартный {@link ApiErrorResponse} для внутренней ошибки
     */
    public static ApiErrorResponse internalError(
            String requestId
    ) {
        return of("Internal server error", requestId);
    }

    /**
     * Строит {@link ApiErrorResponse} на основе {@link ResponseStatusException},
     * заполняя сообщение из HTTP статуса и причины.
     *
     * @param ex        исключение с HTTP статусом
     * @param requestId идентификатор запроса
     * @return готовый ответ об ошибке
     */
    public static ApiErrorResponse fromResponseStatusException(
            ResponseStatusException ex,
            String requestId
    ) {
        HttpStatusCode statusCode = ex.getStatusCode();
        HttpStatus status = HttpStatus.valueOf(statusCode.value());

        String message = ex.getReason();
        if (message == null || message.isBlank()) {
            message = defaultMessageForStatus(status);
        }

        return of(message, requestId);
    }

    /**
     * Сообщение по умолчанию для заданного статуса, если в исключении нет текста.
     */
    private static String defaultMessageForStatus(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Invalid request";
            case UNAUTHORIZED -> "Authentication required";
            case FORBIDDEN -> "Access denied";
            case NOT_FOUND -> "Resource not found";
            case CONFLICT -> "Conflict";
            default -> "Unexpected error";
        };
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;
    }
}
