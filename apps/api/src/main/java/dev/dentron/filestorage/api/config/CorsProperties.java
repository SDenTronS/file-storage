package dev.dentron.filestorage.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;
import java.util.Objects;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(CorsRule actuator, CorsRule internal) {

    public CorsProperties {
        actuator = CorsRule.withDefaults(actuator, "/actuator/**");
        internal = CorsRule.withDefaults(internal, "/**");
    }

    public record CorsRule(
            boolean enabled,
            String pathPattern,
            List<String> allowedOrigins,
            List<String> allowedOriginPatterns,
            List<String> allowedMethods,
            List<String> allowedHeaders,
            List<String> exposedHeaders,
            Long maxAgeSeconds,
            boolean allowCredentials
    ) {
        private static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of("*");
        private static final List<String> DEFAULT_ALLOWED_METHODS =
                List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        private static final List<String> DEFAULT_ALLOWED_HEADERS = List.of("*");
        private static final List<String> EMPTY = List.of();

        static CorsRule withDefaults(CorsRule source, String defaultPathPattern) {
            if (source == null) {
                return new CorsRule(
                        true,
                        defaultPathPattern,
                        DEFAULT_ALLOWED_ORIGINS,
                        EMPTY,
                        DEFAULT_ALLOWED_METHODS,
                        DEFAULT_ALLOWED_HEADERS,
                        EMPTY,
                        3600L,
                        false
                );
            }

            return new CorsRule(
                    source.enabled(),
                    source.pathPattern() == null || source.pathPattern().isBlank()
                            ? defaultPathPattern
                            : source.pathPattern(),
                    normalizeList(source.allowedOrigins(), DEFAULT_ALLOWED_ORIGINS),
                    normalizeList(source.allowedOriginPatterns(), EMPTY),
                    normalizeList(source.allowedMethods(), DEFAULT_ALLOWED_METHODS),
                    normalizeList(source.allowedHeaders(), DEFAULT_ALLOWED_HEADERS),
                    normalizeList(source.exposedHeaders(), EMPTY),
                    source.maxAgeSeconds() == null ? 3600L : source.maxAgeSeconds(),
                    source.allowCredentials()
            );
        }

        public CorsConfiguration toCorsConfiguration() {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(allowedOrigins);
            configuration.setAllowedOriginPatterns(allowedOriginPatterns);
            configuration.setAllowedMethods(allowedMethods);
            configuration.setAllowedHeaders(allowedHeaders);
            configuration.setExposedHeaders(exposedHeaders);
            configuration.setAllowCredentials(allowCredentials);
            configuration.setMaxAge(maxAgeSeconds);
            return configuration;
        }

        private static List<String> normalizeList(List<String> source, List<String> defaults) {
            if (source == null || source.isEmpty()) {
                return defaults;
            }

            return source.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
    }
}
