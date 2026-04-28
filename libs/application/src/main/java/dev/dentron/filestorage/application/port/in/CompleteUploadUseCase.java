package dev.dentron.filestorage.application.port.in;

import dev.dentron.filestorage.application.port.FilePart;
import dev.dentron.filestorage.application.port.NamespaceContext;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Входной порт для завершения multipart-загрузок.
 */
public interface CompleteUploadUseCase {

    /**
     * Завершает multipart-загрузку в указанном namespace.
     *
     * @param ns контекст namespace, которому принадлежит сессия загрузки
     * @param request идентификатор multipart-загрузки и список загруженных частей
     * @return асинхронный результат завершения с идентификатором сохранённого файла
     */
    CompletableFuture<CompleteUploadResult> completeMultipartUpload(NamespaceContext ns, CompleteUploadRequest request);

    /**
     * Команда для завершения multipart-загрузки.
     *
     * @param multipartUploadId идентификатор multipart-загрузки в хранилище
     * @param parts загруженные части в порядке завершения
     */
    record CompleteUploadRequest(
            String multipartUploadId,
            List<FilePart> parts
    ) {}

    /**
     * Результат успешного завершения multipart-загрузки.
     *
     * @param fileId идентификатор сохранённого файла
     * @param etag ETag объекта в хранилище
     */
    record CompleteUploadResult(
            UUID fileId,
            String etag
    ) {}
}
