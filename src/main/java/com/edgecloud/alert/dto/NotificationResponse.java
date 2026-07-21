package com.edgecloud.alert.dto;

import com.edgecloud.alert.entity.AlertType;
import com.edgecloud.alert.entity.NotificationStatus;
import com.edgecloud.alert.entity.Severity;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(

        UUID id,

        UUID alertId,

        AlertType alertType,

        Severity severity,

        String message,

        String sourceService,

        NotificationStatus status,

        LocalDateTime createdAt

) {
}
