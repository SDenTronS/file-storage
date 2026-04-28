package dev.dentron.filestorage.application.port.in;

import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.PresignedUrl;

import java.time.Instant;
import java.util.UUID;

/**
 * Входной порт для выдачи доступа на скачивание файлов.
 */
public interface IssueDownloadUseCase {

    /**
     * Выдаёт presigned URL для скачивания файла.
     *
     * @param ns контекст namespace, которому принадлежит файл
     * @param request идентификатор файла для выдачи URL
     * @return presigned URL для скачивания файла
     */
    PresignedUrl presignGet(NamespaceContext ns, PresignedGetRequest request);

    /**
     * Команда для выдачи presigned URL на скачивание файла.
     *
     * @param fileId идентификатор файла
     */
    record PresignedGetRequest(
            UUID fileId
    ) {}

    /**
     * Обменивает токен скачивания на presigned URL.
     *
     * @param redeemer контекст namespace, который погашает токен
     * @param request ранее выданный токен для погашения
     * @return presigned URL, выданный по токену
     */
    PresignedUrl redeemToken(NamespaceContext redeemer, RedeemTokenRequest request);

    /**
     * Команда для погашения токена скачивания.
     *
     * @param token непрозрачный токен скачивания
     */
    record RedeemTokenRequest(
            String token
    ) {}


    /**
     * Выдаёт токен скачивания для указанной аудитории.
     *
     * @param ns контекст namespace, которому принадлежит файл
     * @param request целевой сервис и идентификатор файла
     * @return выданный токен с метаданными срока действия
     */
    DownloadTokenResponse issueDownloadToken(NamespaceContext ns, IssueTokenRequest request);

    /**
     * Команда для выдачи токена скачивания.
     *
     * @param audienceService сервис, которому разрешено погасить токен
     * @param fileId идентификатор файла
     */
    record IssueTokenRequest(
            String audienceService,
            UUID fileId
    ) {}

    /**
     * Результат успешной выдачи токена скачивания.
     *
     * @param token непрозрачный токен скачивания
     * @param expiresAt момент истечения срока действия токена
     */
    record DownloadTokenResponse(
            String token,
            Instant expiresAt
    ) {}
}
