package dev.dentron.filestorage.api.security.jwt;

public interface JwtTokenVerifier {
    JwtToken validate(String token);
}
