package dev.dentron.filestorage.storages3;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@ConfigurationProperties("minio")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MinIOCredentials {
    String bucket;
    String accessKey;
    String secretKey;
    String endpoint;
    String region;
    Cors cors;
    Init init;
    Lifecycle lifecycle;

    public MinIOCredentials() {
        this.region = (region == null || region.isBlank()) ? "eu-central-1" : region;
        this.cors = cors == null ? Cors.defaultCors() : cors;
        this.init = init == null ? Init.defaultInit() : init;
        this.lifecycle = lifecycle == null ? Lifecycle.defaultLifecycle() : lifecycle;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Cors {
        List<String> allowedOrigins;
        List<String> allowedMethods;
        List<String> allowedHeaders;
        List<String> exposedHeaders;
        int maxAge;

        static Cors defaultCors() {
            return new Cors(
                    List.of("*"),
                    List.of("GET", "HEAD", "PUT", "POST", "DELETE", "OPTIONS"),
                    List.of("*"),
                    List.of("ETag"),
                    3600
            );
        }
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Lifecycle {
        int abortIncompleteMultipartUploadDays;
        String prefix;

        static Lifecycle defaultLifecycle() {
            return new Lifecycle(2, "svc/");
        }
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Init {
        boolean createBucket;
        boolean applyCors;
        boolean applyLifecycle;

        static Init defaultInit() {
            return new Init(true, false, false);
        }
    }
}
