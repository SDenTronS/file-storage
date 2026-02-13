package dev.dentron.filestorage.domain.exception;

public class DownloadTokenExpiredException extends RuntimeException {
    public DownloadTokenExpiredException() {
        super("Download token is expired");
    }
}