package dev.dentron.filestorage.api.security.jwt;

import dev.dentron.filestorage.api.security.ServiceDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String AUTH_HEADER = "Authorization";
    private static final String AUTH_TOKEN = "Bearer ";
    public final JwtTokenVerifier jwtTokenVerifier;
    public final String thisServiceName;

    public JwtAuthenticationFilter(JwtTokenVerifier jwtTokenVerifier,
                                   @Value("${app.name:file-storage}") String thisServiceName) {
        this.jwtTokenVerifier = jwtTokenVerifier;
        this.thisServiceName = thisServiceName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(AUTH_HEADER);

        if (authHeader == null || !authHeader.startsWith(AUTH_TOKEN)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(AUTH_TOKEN.length());
        JwtToken decoded = jwtTokenVerifier.validate(token);

        if (decoded == null || !decoded.audience().contains(thisServiceName)) {
            filterChain.doFilter(request, response);
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();

        ServiceDetails details = new ServiceDetails(decoded.userDetails());
        Authentication authentication = new UsernamePasswordAuthenticationToken(details, null, List.of());
        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);
        filterChain.doFilter(request, response);
    }
}
