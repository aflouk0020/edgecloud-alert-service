package com.edgecloud.alert.util;

import com.edgecloud.alert.entity.AlertType;

public final class RootCauseSuggestionResolver {

    private RootCauseSuggestionResolver() {
    }

    public static String resolve(AlertType alertType) {
        if (alertType == null) {
            return "No root-cause suggestion is currently available.";
        }

        return switch (alertType) {
            case SERVICE_DOWN ->
                    "Service may be unavailable or its container may have stopped.";

            case HIGH_LATENCY ->
                    "Service may be overloaded, resource-constrained, or experiencing network delays.";

            case DEVICE_OFFLINE ->
                    "Edge device may be disconnected, powered off, or unable to reach the platform.";

            case DATABASE_FAILURE ->
                    "Database may be unavailable, unhealthy, or using incorrect connection settings.";

            case CONTAINER_FAILURE ->
                    "Container may have stopped, failed its health check, or exhausted available resources.";
        };
    }
}
