package dev.dentron.filestorage.api.security.jwt;

public interface JwtAuthUtil {
    boolean validate(String token);
}
