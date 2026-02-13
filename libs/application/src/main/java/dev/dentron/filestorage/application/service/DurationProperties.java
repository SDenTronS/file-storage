package dev.dentron.filestorage.application.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "storage")
public record DurationProperties(Token token, Presign presign, Multipart multipart) {

    public record Token (Duration ttl, Duration presignTtl) {}
    public record Presign(Duration getTtl, Duration putTtl) {}
    public record Multipart(Duration create, Duration complete, Duration sessionTtl) {}
}
