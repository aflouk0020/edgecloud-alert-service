package com.edgecloud.alert.security;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class EdgeCloudJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public EdgeCloudJwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v2/")
                || request.getRequestURI().startsWith("/actuator/health")
                || request.getRequestURI().startsWith("/actuator/info");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            SecurityExceptionHandler.writeUnauthorized(response, "Missing authentication");
            return;
        }

        String token = header.substring(7).trim();
        if (token.isEmpty() || !jwtService.isValid(token)) {
            SecurityExceptionHandler.writeUnauthorized(response, "Invalid or expired token");
            return;
        }

        try {
            UUID userId = jwtService.extractUserId(token);
            String role = jwtService.extractRole(token);
            if (role == null || role.isBlank()) {
                SecurityExceptionHandler.writeUnauthorized(response, "Invalid or expired token");
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(
                    new EdgeCloudJwtAuthenticationToken(userId, role, token));
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            SecurityExceptionHandler.writeUnauthorized(response, "Invalid or expired token");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}