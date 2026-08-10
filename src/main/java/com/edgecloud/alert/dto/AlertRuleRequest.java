package com.edgecloud.alert.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.edgecloud.alert.entity.AlertRuleComparisonOperator;
import com.edgecloud.alert.entity.AlertRuleMetricType;
import com.edgecloud.alert.entity.Severity;

public record AlertRuleRequest(
        String name,
        String description,
        AlertRuleMetricType metricType,
        BigDecimal thresholdValue,
        AlertRuleComparisonOperator comparisonOperator,
        Severity severity,
        boolean enabled,
        String deviceId,
        UUID serviceId) {
}