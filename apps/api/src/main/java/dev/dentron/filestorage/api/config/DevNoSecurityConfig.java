package dev.dentron.filestorage.api.config;

import dev.dentron.filestorage.api.security.jwt.DevJwtTokenVerifier;
import dev.dentron.filestorage.api.security.jwt.JwtAuthenticationFilter;
import dev.dentron.filestorage.api.security.jwt.JwtTokenVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@ConditionalOnBooleanProperty(prefix = "app.security", value = "enabled", havingValue = false, matchIfMissing = true)
@EnableWebSecurity
public class DevNoSecurityConfig {
    @Bean
    public JwtAuthenticationFilter jwtDevAuthenticationFilter(JwtTokenVerifier jwtTokenVerifier) {
        return new JwtAuthenticationFilter(jwtTokenVerifier, "dev");
    }

    @Bean
    public JwtTokenVerifier devTokenVerifier() {
        return new DevJwtTokenVerifier();
    }

    @Bean
    public SecurityFilterChain devFilterChain(JwtAuthenticationFilter filter, HttpSecurity http) {
        return http
                .authorizeHttpRequests((requests) -> requests.anyRequest().permitAll())
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }
}
