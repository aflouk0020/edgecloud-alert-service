package com.edgecloud.alert.dto;

import com.edgecloud.alert.entity.AlertType;
import com.edgecloud.alert.entity.Severity;
import java.time.LocalDateTime;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        AlertType alertType,
        Severity severity,
        String message,
        String sourceService,
        String status,
        boolean resolved,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt
) {
}
