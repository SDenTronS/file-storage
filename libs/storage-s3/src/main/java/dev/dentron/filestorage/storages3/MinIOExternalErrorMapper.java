package dev.dentron.filestorage.storages3;

import dev.dentron.filestorage.application.exception.ExternalStorageException;
import dev.dentron.filestorage.common.util.ExceptionUtils;
import io.minio.errors.ErrorResponseException;
import org.springframework.stereotype.Component;

@Component
public class MinIOExternalErrorMapper {

    public RuntimeException map(
            Throwable throwable,
            String operation,
            String bucket,
            String objectKey
    ) {
        return map(throwable, operation, bucket, objectKey, null);
    }

    public RuntimeException map(
            Throwable throwable,
            String operation,
            String bucket,
            String objectKey,
            String uploadId
    ) {
        Throwable cause = ExceptionUtils.unwrap(throwable);
        if (cause instanceof RuntimeException runtime && isAlreadyMapped(runtime)) {
            return runtime;
        }

        String code = extractCode(cause);
        return new ExternalStorageException(operation, bucket, objectKey, code, cause);
    }

    public boolean isObjectMissing(Throwable throwable) {
        return "NoSuchKey".equals(extractCode(ExceptionUtils.unwrap(throwable)));
    }

    public boolean isAbortAlreadyGone(Throwable throwable) {
        String code = extractCode(ExceptionUtils.unwrap(throwable));
        return "NoSuchUpload".equals(code) || "NoSuchKey".equals(code);
    }

    private boolean isAlreadyMapped(RuntimeException runtime) {
        return runtime instanceof ExternalStorageException;
    }

    private String extractCode(Throwable throwable) {
        if (throwable instanceof ErrorResponseException e) {
            return e.errorResponse().code();
        }

        return null;
    }
}
