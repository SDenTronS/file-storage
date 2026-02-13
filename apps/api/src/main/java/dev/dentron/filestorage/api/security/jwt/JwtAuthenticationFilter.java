package dev.dentron.filestorage.api.security.jwt;

import dev.dentron.filestorage.api.security.ServiceDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String AUTH_HEADER = "Authorization";
    private static final String AUTH_TOKEN = "Bearer ";

    //TODO дописать фильтр
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(AUTH_HEADER);

        if (authHeader == null || !authHeader.startsWith(AUTH_TOKEN)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(AUTH_TOKEN.length());
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        ServiceDetails details = new ServiceDetails("aboba");
        Authentication authentication = new UsernamePasswordAuthenticationToken(details, null, null);
        context.setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
