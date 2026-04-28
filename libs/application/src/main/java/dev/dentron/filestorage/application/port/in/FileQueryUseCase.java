package dev.dentron.filestorage.application.port.in;

import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.domain.FileObject;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Входной порт для запросов метаданных файлов.
 */
public interface FileQueryUseCase {
    /**
     * Метаданные файла, возвращаемые операциями чтения.
     *
     * @param fileId идентификатор файла
     * @param owner идентификатор владельца файла
     * @param bucket имя бакета в хранилище
     * @param objectKey ключ объекта в хранилище
     * @param originalName исходное имя загруженного файла
     * @param size размер сохранённого файла в байтах
     * @param sha256 контрольная сумма SHA-256
     * @param etag ETag объекта в хранилище
     * @param contentType сохранённый content type
     * @param status текущий статус жизненного цикла файла
     * @param createdAt момент создания файла
     */
    record FileMetadata(
            UUID fileId,
            String owner,
            String bucket,
            String objectKey,
            String originalName,
            Long size,
            String sha256,
            String etag,
            String contentType,
            FileObject.Status status,
            Instant createdAt
    ) {
    }

    /**
     * Возвращает список файлов, доступных в указанном namespace.
     *
     * @param ns контекст namespace для запроса
     * @param request параметры пагинации
     * @return страница файлов с необязательным курсором продолжения
     */
    ListFilesResult listFiles(NamespaceContext ns, ListFilesRequest request);

    /**
     * Параметры запроса на получение списка файлов.
     *
     * @param cursor курсор пагинации из предыдущего результата
     * @param limit максимальное количество элементов в ответе
     */
    record ListFilesRequest(
            String cursor,
            Integer limit
    ) {
    }

    /**
     * Страница результата операции получения списка файлов.
     *
     * @param items элементы метаданных файлов на текущей странице
     * @param nextCursor курсор следующей страницы или {@code null}, если страниц больше нет
     */
    record ListFilesResult(
            List<FileMetadata> items,
            String nextCursor
    ) {
    }

    /**
     * Загружает метаданные файла по его идентификатору.
     *
     * @param ns контекст namespace для запроса
     * @param request идентификатор файла
     * @return метаданные запрошенного файла
     */
    FileMetadata getFile(NamespaceContext ns, GetFileRequest request);

    /**
     * Параметры запроса на получение метаданных файла.
     *
     * @param fileId идентификатор файла
     */
    record GetFileRequest(
            UUID fileId
    ) {
    }
}
