package dev.dentron.filestorage.api.config;

import dev.dentron.filestorage.api.security.jwt.Auth0JwtTokenVerifier;
import dev.dentron.filestorage.api.security.jwt.JwtAuthenticationFilter;
import dev.dentron.filestorage.api.security.jwt.JwtTokenVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public JwtTokenVerifier tokenVerifier(@Value("${JWT_PUBLIC_KEY:${jwt.public-key:}}") String publicKey) {
        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalStateException("JWT public key is not configured. Set JWT_PUBLIC_KEY env variable.");
        }
        return new Auth0JwtTokenVerifier(publicKey);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        registerCorsRule(source, corsProperties.actuator());
        registerCorsRule(source, corsProperties.internal());
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           CorsConfigurationSource corsConfigurationSource) {
        return http
                .authorizeHttpRequests((requests) -> requests
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterAfter(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/actuator/**"))
                .build();
    }

    private static void registerCorsRule(
            UrlBasedCorsConfigurationSource source,
            CorsProperties.CorsRule corsRule
    ) {
        if (!corsRule.enabled()) {
            return;
        }

        source.registerCorsConfiguration(corsRule.pathPattern(), corsRule.toCorsConfiguration());
    }
}
