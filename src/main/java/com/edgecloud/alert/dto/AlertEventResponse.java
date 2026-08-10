package com.edgecloud.alert.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.edgecloud.alert.entity.AlertEvent;
import com.edgecloud.alert.entity.AlertEventSourceType;
import com.edgecloud.alert.entity.AlertEventStatus;
import com.edgecloud.alert.entity.AlertRuleComparisonOperator;
import com.edgecloud.alert.entity.AlertRuleMetricType;
import com.edgecloud.alert.entity.Severity;

public record AlertEventResponse(
        UUID alertId,
        UUID alertRuleId,
        String alertRuleName,
        UUID projectId,
        AlertEventSourceType sourceType,
        String sourceId,
        AlertRuleMetricType metricType,
        BigDecimal observedValue,
        BigDecimal thresholdValue,
        AlertRuleComparisonOperator comparisonOperator,
        Severity severity,
        AlertEventStatus status,
        Instant triggeredAt,
        Instant lastObservedAt,
        Instant resolvedAt,
        UUID ownerUserId,
        String ownerDisplayName,
        Instant acknowledgedAt,
        Instant ownershipChangedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static AlertEventResponse from(AlertEvent event) {
        return new AlertEventResponse(
                event.getId(), event.getAlertRuleId(), event.getAlertRuleName(), event.getProjectId(),
                event.getSourceType(), event.getSourceId(), event.getMetricType(), event.getObservedValue(),
                event.getThresholdValue(), event.getComparisonOperator(), event.getSeverity(), event.getStatus(),
                event.getTriggeredAt(), event.getLastObservedAt(), event.getResolvedAt(),
                event.getOwnerUserId(), event.getOwnerDisplayName(), event.getAcknowledgedAt(),
                event.getOwnershipChangedAt(),
                event.getCreatedAt(), event.getUpdatedAt());
    }
}
