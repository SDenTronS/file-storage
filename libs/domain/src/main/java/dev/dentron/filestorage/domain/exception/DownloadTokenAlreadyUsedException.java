package dev.dentron.filestorage.domain.exception;

public class DownloadTokenAlreadyUsedException extends RuntimeException {
    public DownloadTokenAlreadyUsedException() {
        super("Download token is already used");
    }
}
