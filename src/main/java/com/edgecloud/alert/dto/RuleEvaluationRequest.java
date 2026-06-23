package com.edgecloud.alert.dto;

public record RuleEvaluationRequest(
        String serviceName,
        String serviceStatus,
        Long responseTimeMs,
        String deviceName,
        String deviceStatus
) {
}
