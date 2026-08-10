package com.edgecloud.alert.security;

import java.io.IOException;
import java.time.LocalDateTime;

import jakarta.servlet.http.HttpServletResponse;

public final class SecurityExceptionHandler {

    private SecurityExceptionHandler() {
    }

    public static void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", message);
    }

    public static void writeForbidden(HttpServletResponse response, String message) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden", message);
    }

    private static void write(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("""
                {"timestamp":"%s","status":%d,"error":"%s","message":"%s"}
                """.formatted(LocalDateTime.now(), status, error, message));
    }
}