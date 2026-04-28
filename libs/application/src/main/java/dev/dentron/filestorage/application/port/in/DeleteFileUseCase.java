package dev.dentron.filestorage.application.port.in;

import dev.dentron.filestorage.application.port.NamespaceContext;

import java.util.UUID;

/**
 * Входной порт для удаления сохранённых файлов.
 */
public interface DeleteFileUseCase {
    /**
     * Удаляет файл из указанного namespace.
     *
     * @param ns контекст namespace, которому принадлежит файл
     * @param request идентификатор файла, который нужно удалить
     */
    void deleteFile(NamespaceContext ns, DeleteFileRequest request);

    /**
     * Команда для удаления файла.
     *
     * @param fileId идентификатор файла
     */
    record DeleteFileRequest(
            UUID fileId
    ) {}
}
