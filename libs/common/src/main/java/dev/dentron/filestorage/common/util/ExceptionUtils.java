package dev.dentron.filestorage.common.util;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class ExceptionUtils {
    public static Throwable unwrap(Throwable ex) {
        while (ex instanceof CompletionException || ex instanceof ExecutionException) {
            if (ex.getCause() == null) {
                break;
            }

            ex = ex.getCause();
        }

        return ex;
    }
}
