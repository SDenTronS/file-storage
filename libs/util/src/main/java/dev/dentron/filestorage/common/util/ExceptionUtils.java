package dev.dentron.filestorage.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class ExceptionUtils {
    private static final Logger log = LoggerFactory.getLogger(ExceptionUtils.class);

    public static Throwable unwrap(Throwable ex) {
        while (ex instanceof CompletionException || ex instanceof ExecutionException) {
            if (ex.getCause() == null) {
                break;
            }

            ex = ex.getCause();
        }

        return ex;
    }

    public static void closeQuietly(AutoCloseable closeable, Throwable primaryError) {
        if (closeable == null) {
            return;
        }

        try {
            closeable.close();
        } catch (Exception e) {
            if (primaryError != null) {
                primaryError.addSuppressed(e);
            }

            log.warn("Failed to close resource", e);
        }
    }
}
