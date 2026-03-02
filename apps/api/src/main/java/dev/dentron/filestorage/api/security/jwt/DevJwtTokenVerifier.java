package dev.dentron.filestorage.api.security.jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class DevJwtTokenVerifier implements JwtTokenVerifier {

    @Override
    public JwtToken validate(String token) {
        return new JwtToken(
                token,
                Instant.now().plus(Duration.ofHours(1)),
                List.of("dev"));
    }
}
