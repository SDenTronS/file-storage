package dev.dentron.filestorage.api.config;

import dev.dentron.filestorage.api.security.jwt.RSAJwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public RSAJwtUtil jwtUtil(@Value("${jwt.public-key:-----BEGIN PUBLIC KEY-----\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArHsi/3Yq+719jpSNYdd0\n" +
            "mD2E95NyQuHBYgg4Uvg9r+As/RHSx+bOUOWjC05unKBU4uj9+fQ6yd0Wbkk2DqxS\n" +
            "24UVQtYik6Ze7f0FX6b5W6L/hVKp/LF2IalM87lClWpxJaoUwvouajMPkSs5Z3ok\n" +
            "6+Hh6cfsd6crPDHqGDaUEOuQZ2dvvgti+mVn1OHfFz9ECcxcKVO3Up0lK8T2R5HS\n" +
            "DFUypy2e3SulM7I91H/Bk3EHNjbGojfcNNkAuQAjpLd0tLrIPloa63QQU59+zZba\n" +
            "l+ZekrSSeFGxiHb9zkl66VyWMVWPJUOM5hR76YAqm8TUmvM5TEM22xsaShS3pH5e\n" +
            "KwIDAQAB\n" +
            "-----END PUBLIC KEY-----}") String publicKey) {
        return new RSAJwtUtil(publicKey);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
         return http
                 .authorizeHttpRequests((requests) -> requests
                         .requestMatchers("/actuator/**").permitAll()
                         .anyRequest().permitAll()
                 )
                 .csrf(csrf -> csrf.ignoringRequestMatchers("/actuator/**"))
                 .build();
    }
}
