package com.edgecloud.alert.dto;

public record AlertSummaryResponse(
        long totalAlerts,
        long activeAlerts,
        long resolvedAlerts,
        long lowSeverityAlerts,
        long mediumSeverityAlerts,
        long highSeverityAlerts
) {
}
