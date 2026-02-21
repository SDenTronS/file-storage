package dev.dentron.filestorage.api.security.jwt;

import java.time.Instant;
import java.util.List;

public record JwtToken(
    String userDetails,
    Instant expiresAt,
    List<String> audience
) { }
