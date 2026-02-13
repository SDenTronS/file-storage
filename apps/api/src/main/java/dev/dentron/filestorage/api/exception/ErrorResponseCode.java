package dev.dentron.filestorage.api.exception;

public enum ErrorResponseCode {

    // REST / WS errors
    UNAUTHORIZED,
    INTERNAL_SERVER_ERROR,
    INVALID_REQUEST,
    ACCESS_DENIED,
    NOT_FOUND,

    // WS errors
    DELIVERY_ERROR,
}
