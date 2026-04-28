package dev.dentron.filestorage.application.port.in;

import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.PresignedUrl;
import org.apache.commons.lang3.function.FailableSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Входной порт для запуска загрузок и выдачи URL для загрузки.
 */
public interface CreateUploadUseCase {

    /**
     * Асинхронно создаёт сессию multipart-загрузки.
     *
     * @param ns контекст namespace, в котором будет создан файл
     * @param request параметры создания загрузки
     * @return асинхронный результат с метаданными сессии загрузки
     */
    CompletableFuture<CreateUploadResult> createMultipartUploadSessionAsync(NamespaceContext ns, CreateUploadRequest request);

    /**
     * Загружает файл напрямую через поставщик входного потока.
     *
     * @param ns контекст namespace, в котором будет создан файл
     * @param request параметры прямой загрузки и поставщик потока
     * @return асинхронный результат прямой загрузки
     */
    CompletableFuture<DirectUploadResult> uploadFile(NamespaceContext ns, DirectUploadRequest request);

    /**
     * Команда для создания сессии multipart-загрузки.
     *
     * @param prefix префикс ключа объекта в хранилище
     * @param originalFileName исходное имя файла на стороне клиента
     * @param expectedContentType ожидаемый content type загружаемого файла
     * @param override признак того, можно ли перезаписывать существующий объект
     * @param sizeBytes ожидаемый размер файла в байтах
     */
    record CreateUploadRequest(
            String prefix,
            String originalFileName,
            String expectedContentType,
            boolean override,
            long sizeBytes
    ) {}

    /**
     * Результат создания сессии multipart-загрузки.
     *
     * @param id идентификатор сессии загрузки
     * @param multipartUploadId идентификатор multipart-загрузки в хранилище
     * @param fileId зарезервированный идентификатор файла
     * @param expiresAt момент истечения срока действия сессии загрузки
     */
    record CreateUploadResult(
            UUID id,
            String multipartUploadId,
            UUID fileId,
            Instant expiresAt
    ) {}

    /**
     * Команда для прямой загрузки файла.
     *
     * @param prefix префикс ключа объекта в хранилище
     * @param originalFileName исходное имя файла на стороне клиента
     * @param expectedContentType ожидаемый content type загружаемого файла
     * @param sizeBytes размер файла в байтах
     * @param inputStreamSupplier поставщик, который открывает поток данных для загрузки
     */
    record DirectUploadRequest(
            String prefix,
            String originalFileName,
            String expectedContentType,
            long sizeBytes,
            FailableSupplier<InputStream, IOException> inputStreamSupplier
    ) {}

    /**
     * Результат успешной прямой загрузки.
     *
     * @param fileId идентификатор сохранённого файла
     * @param etag ETag объекта в хранилище
     */
    record DirectUploadResult(
            UUID fileId,
            String etag
    ) {}

    /**
     * Выдаёт presigned URL для загрузки части multipart-загрузки.
     *
     * @param ns контекст namespace, которому принадлежит multipart-загрузка
     * @param request идентификатор multipart-загрузки и номер части
     * @return presigned URL для загрузки части
     */
    PresignedUrl presignMultipartPut(NamespaceContext ns, PresignedMultipartPutRequest request);

    /**
     * Команда для выдачи presigned URL на часть multipart-загрузки.
     *
     * @param multipartUploadId идентификатор multipart-загрузки в хранилище
     * @param partNumber номер части, начиная с 1
     */
    record PresignedMultipartPutRequest(
            String multipartUploadId,
            int partNumber
    ) {}

    /**
     * Выдаёт presigned URL для сессии прямой загрузки.
     *
     * @param ns контекст namespace, которому принадлежит сессия загрузки
     * @param request идентификатор сессии прямой загрузки
     * @return presigned URL для загрузки содержимого файла
     */
    PresignedUrl presignPut(NamespaceContext ns, PresignedPutRequest request);

    /**
     * Команда для выдачи presigned URL на прямую загрузку.
     *
     * @param sessionId идентификатор сессии загрузки
     */
    record PresignedPutRequest(
            UUID sessionId
    ) {}
}
