package com.edgecloud.alert.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.edgecloud.alert.entity.AlertNotificationOutbox;
import com.edgecloud.alert.entity.NotificationLifecycleEventType;
import com.edgecloud.alert.entity.Severity;

public record AlertLifecycleNotificationRequest(
        UUID sourceEventId,
        UUID alertEventId,
        UUID projectId,
        NotificationLifecycleEventType eventType,
        Severity severity,
        String ruleName,
        String metricType,
        String sourceType,
        String sourceId,
        BigDecimal observedValue,
        BigDecimal thresholdValue,
        Instant occurredAt) {

    public static AlertLifecycleNotificationRequest from(AlertNotificationOutbox item) {
        return new AlertLifecycleNotificationRequest(item.getSourceEventId(), item.getAlertEventId(),
                item.getProjectId(), item.getEventType(), item.getSeverity(), item.getRuleName(),
                item.getMetricType(), item.getSourceType(), item.getSourceId(), item.getObservedValue(),
                item.getThresholdValue(), item.getOccurredAt());
    }
}
