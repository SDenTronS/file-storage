package dev.dentron.filestorage.application.port.in;

import dev.dentron.filestorage.application.port.NamespaceContext;

import java.util.UUID;

/**
 * Входной порт для отмены сессий загрузки.
 */
public interface AbortUploadUseCase {
    /**
     * Отменяет активную сессию загрузки в указанном namespace.
     *
     * @param ns контекст namespace, которому принадлежит сессия загрузки
     * @param request идентификатор сессии загрузки, которую нужно отменить
     */
    void abortUpload(NamespaceContext ns, AbortUploadRequest request);

    /**
     * Команда для отмены сессии загрузки.
     *
     * @param sessionId идентификатор сессии загрузки
     */
    record AbortUploadRequest(
            UUID sessionId
    ) {}
}
