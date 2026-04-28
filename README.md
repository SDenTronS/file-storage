# File Storage

`file-storage` - многомодульный backend-сервис для хранения файлов в S3-совместимом хранилище.

Проект разделён на два приложения:

- `api` принимает HTTP-запросы, создаёт upload-сессии, выдаёт presigned URL, возвращает метаданные файлов и генерирует одноразовые токены на скачивание;
- `worker` асинхронно обрабатывает события после загрузки и удаления файлов, определяет MIME-тип через Apache Tika и обновляет итоговый статус файла.

В качестве инфраструктуры используются PostgreSQL, Kafka и MinIO.

Поддерживаются:

- multipart upload через presigned `PUT` URL;
- direct upload небольших файлов через `multipart/form-data`;
- presigned `GET` URL на скачивание;
- одноразовые токены для межсервисного доступа к файлам;
- асинхронная пост-обработка через outbox + Kafka;
- изоляция файлов по сервису-владельцу.

## Архитектурные особенности

- `api` управляет upload-сессиями и presigned URL, но не проксирует multipart-трафик через себя.
- `worker` вынесен отдельно, чтобы пост-обработка не блокировала HTTP API.
- Основная логика разделена по модулям `domain`, `application`, `persistence`, `storage-s3` и `messaging-kafka`.
- Для публикации событий используется outbox pattern.

## Архитектура

`api` работает с upload-сессиями, метаданными и presigned URL. `worker` обрабатывает события после загрузки и удаления файлов, определяет MIME-тип и выполняет фоновую обработку.

Метаданные и outbox хранятся в PostgreSQL, обмен событиями идёт через Kafka, файлы лежат в MinIO / S3-compatible storage. Файлы изолированы по сервису-владельцу и размещаются в пространстве имён с префиксом `svc/<serviceId>`.

## Структура репозитория

| Путь | Назначение |
| --- | --- |
| `apps/api` | HTTP API, security, exception handling, публикация outbox в Kafka |
| `apps/worker` | Kafka listeners, MIME detection, cleanup scheduler |
| `libs/application` | use case слой и orchestration между портами |
| `libs/domain` | доменные модели и исключения |
| `libs/persistence` | JPA entities, repositories, persistence adapters, Flyway migration |
| `libs/storage-s3` | адаптер MinIO / S3 |
| `libs/messaging-kafka` | Kafka config и topic properties |
| `libs/util` | общие утилиты |
| `build-logic` | общие Gradle conventions |
| `load-tests/k6` | k6-сценарии |

## Технологический стек

| Компонент | Что используется |
| --- | --- |
| Язык и runtime | Java 21 |
| Сборка | Gradle Wrapper 8.14 |
| Framework | Spring Boot 4.0.3 |
| API | Spring Web MVC, Validation, springdoc OpenAPI |
| Безопасность | Spring Security, `java-jwt` 4.5.0 |
| База данных | PostgreSQL, Spring Data JPA, Flyway |
| Messaging | Apache Kafka 4.1.1, Spring Kafka |
| Object storage | MinIO / S3-compatible storage |
| Анализ содержимого | Apache Tika 3.2.2 |
| Тесты | JUnit 6, Testcontainers 2.0.3 |

## Ключевые сценарии

- Multipart upload: клиент создаёт upload-сессию, получает presigned URL для частей и завершает загрузку через отдельный endpoint.
- Direct upload: небольшой файл можно загрузить одним запросом без multipart-сценария.
- Скачивание: файл можно получить либо через presigned `GET` URL, либо через одноразовый токен для межсервисного доступа.

## Быстрый старт

### Требования

- JDK 21;
- Docker Engine / Docker Desktop с Compose v2;
- доступ к Docker daemon, если нужны контейнеры или Testcontainers.

### Подготовка `.env`

В репозитории есть шаблон:

```powershell
Copy-Item .env.example .env
```

Важно:

- корневой `.env` использует `docker compose`;
- `bootRun` этот файл автоматически не подхватывает;
- `.env.example` ближе к локальному `bootRun`, чем к запуску приложений внутри Docker.

### Рекомендуемый вариант: инфраструктура в Docker, приложения локально

Поднять PostgreSQL, Kafka и MinIO:

```powershell
docker compose -f apps/api/src/systemTest/resources/compose.system-test.yaml up -d
```

Порты:

- PostgreSQL: `localhost:15432`
- Kafka: `localhost:19092`
- MinIO API: `http://localhost:19000`
- MinIO Console: `http://localhost:19001`

Минимальные переменные окружения для PowerShell:

```powershell
$env:SPRING_DATASOURCE_HOST = "localhost"
$env:SPRING_DATASOURCE_PORT = "15432"
$env:SPRING_DATASOURCE_DB = "file_storage"
$env:SPRING_DATASOURCE_USERNAME = "postgres"
$env:SPRING_DATASOURCE_PASSWORD = "postgres"
$env:SPRING_KAFKA_BOOTSTRAP_SERVERS = "localhost:19092"
$env:MINIO_ENDPOINT = "http://localhost:19000"
$env:MINIO_BUCKET = "bucket-main"
$env:MINIO_ACCESS_KEY = "minioadmin"
$env:MINIO_SECRET_KEY = "minioadmin"
$env:MINIO_REGION = "eu-central-1"
$env:APP_SECURITY_ENABLED = "false"
$env:API_DOWNLOAD_TOKEN_SECRET = "system-test-secret"
```

Запуск приложений:

```powershell
.\gradlew.bat :apps:api:bootRun
.\gradlew.bat :apps:worker:bootRun
```

### Полный запуск через Docker Compose

`compose.yaml` поднимает `api`, `worker`, PostgreSQL, Kafka и MinIO Aistor.

Минимальные override для `.env`:

```dotenv
SPRING_DATASOURCE_HOST=db
SPRING_DATASOURCE_PORT=5432
SPRING_KAFKA_BOOTSTRAP_SERVERS=broker:9092
MINIO_ENDPOINT=http://min-io:9000
MINIO_LICENSE_FILE=./secrets/minio/minio.license
```

Запуск:

```powershell
docker compose up --build
```

После старта доступны:

- API: `http://localhost:8080`
- PostgreSQL: `localhost:9876`
- Kafka: `localhost:9092`
- MinIO API: `http://localhost:9000`
- MinIO Console: `http://localhost:9001`

## Конфигурация

Основные настройки лежат в:

- `apps/api/src/main/resources/application.yaml`
- `apps/worker/src/main/resources/application.yaml`
- `.env.example`

Ключевые переменные окружения:

| Переменная | Где используется | Назначение |
| --- | --- | --- |
| `APP_NAME` | `api` | имя текущего сервиса для JWT audience check |
| `APP_SECURITY_ENABLED` | `api` | включает или выключает JWT verification |
| `API_JWT_PUBLIC_KEY` | `api` | публичный ключ для RSA JWT verification |
| `API_DOWNLOAD_TOKEN_SECRET` | `api`, `worker` | секрет для выпуска и погашения одноразовых токенов |
| `SPRING_DATASOURCE_*` | `api`, `worker` | подключение к PostgreSQL |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `api`, `worker` | bootstrap servers Kafka |
| `MINIO_ENDPOINT` | `api`, `worker` | endpoint MinIO/S3 |
| `MINIO_BUCKET` | `api`, `worker` | bucket для файлов |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | `api`, `worker` | credentials для MinIO |
| `APP_KAFKA_ENABLED` | `api`, `worker` | включает Kafka beans/listeners |
| `APP_MINIO_ENABLED` | `api`, `worker` | включает S3 adapter beans |
| `STORAGE_*` | `api`, `worker` | TTL для presigned URL, token и upload-сессий |

Дополнительные настройки находятся под префиксами:

- `app.kafka.*`
- `app.cors.*`
- `storage.*`
- `minio.*`

По умолчанию bucket создаётся автоматически при старте приложения, если его ещё нет.

## Безопасность

- При `APP_SECURITY_ENABLED=false` dev-verifier принимает любой `Bearer <serviceId>`, а строка токена становится `serviceId`.
- При `APP_SECURITY_ENABLED=true` используется RSA JWT verification через `API_JWT_PUBLIC_KEY`.
- JWT audience должен содержать имя текущего сервиса из `APP_NAME` / `app.name`.
- Для межсервисной выдачи файла есть отдельный сценарий с одноразовым токеном на скачивание.

## HTTP API

Основные endpoint'ы:

| Метод | Путь | Назначение |
| --- | --- | --- |
| `POST` | `/v1/uploads` | создать multipart upload-сессию |
| `POST` | `/v1/uploads/direct` | загрузить небольшой файл одним запросом |
| `POST` | `/v1/uploads/{multipartUploadId}?partNumber=N` | получить presigned URL для части |
| `POST` | `/v1/uploads/{multipartUploadId}/complete` | завершить multipart upload |
| `DELETE` | `/v1/uploads/{sessionId}` | abort upload-сессию по UUID `id` |
| `GET` | `/v1/files?cursor=...&limit=...` | получить страницу файлов текущего сервиса |
| `GET` | `/v1/files/{fileId}` | получить метаданные файла |
| `DELETE` | `/v1/files/{fileId}` | пометить файл удалённым |
| `POST` | `/v1/files/{fileId}/download-url` | получить presigned URL на скачивание |
| `POST` | `/v1/files/{fileId}/download-token` | выдать одноразовый token другому сервису |
| `POST` | `/v1/download-tokens/redeem?token=...` | погасить token и получить presigned URL |

Swagger UI по умолчанию доступен по `GET /swagger-ui/index.html`, raw schema - по `GET /v3/api-docs`.

## Сборка и проверка

Собрать jar-файлы:

```powershell
.\gradlew.bat :apps:api:bootJar :apps:worker:bootJar
```

Собрать Docker-образы:

```powershell
docker build --target api -t file-storage-api:latest .
docker build --target worker -t file-storage-worker:latest .
```

Запустить тесты:

```powershell
.\gradlew.bat test
```

Полная Gradle-проверка:

```powershell
.\gradlew.bat check
```

Отдельной Gradle-задачи `systemTest` сейчас нет: директория `apps/api/src/systemTest` используется как manual/in-IDE end-to-end окружение.

## Нагрузочное тестирование

В репозитории есть два k6-сценария:

- `load-tests/k6/scripts/script.js` - multipart upload + delete;
- `load-tests/k6/scripts/direct-upload.js` - direct upload + delete.

Минимальный запуск:

```powershell
$env:AUTH_TOKEN = "media-service"
$env:BASE_URL = "http://localhost:8080"
k6 run .\load-tests\k6\scripts\script.js
k6 run .\load-tests\k6\scripts\direct-upload.js
```

## Наблюдаемость

- только `apps/api` подключает Spring Boot Actuator;
- наружу выставлены `/actuator/health`, `/actuator/info`, `/actuator/beans`, `/actuator/mappings`, `/actuator/env`;
- для разработки включены повышенные уровни логирования для `dev.dentron`, `io.minio` и `org.flywaydb`.

## Практические замечания

- Если файл долго висит в `UPLOADED`, первым делом стоит проверить, что `worker` запущен и Kafka доступна.
- Если `docker compose up` падает на MinIO Aistor, проверьте `MINIO_LICENSE_FILE`.
- `bootRun` не читает корневой `.env` автоматически, переменные окружения нужно экспортировать вручную или настроить в IDE.
- `GET /v1/files` использует cursor pagination: `limit` от `1` до `100`, по умолчанию `50`.
- Если при создании upload-сессии указан `contentType`, `worker` сверит его с фактически определённым MIME и может перевести файл в `REJECTED`.

## Лицензия

Файл `LICENSE` в репозитории сейчас отсутствует. Условия лицензирования нужно определить отдельно.
