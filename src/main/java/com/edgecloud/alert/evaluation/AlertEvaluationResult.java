package com.edgecloud.alert.evaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.edgecloud.alert.entity.AlertRuleComparisonOperator;
import com.edgecloud.alert.entity.AlertRuleMetricType;
import com.edgecloud.alert.entity.Severity;

public record AlertEvaluationResult(
        UUID ruleId,
        UUID projectId,
        AlertEvaluationSourceType sourceType,
        String sourceId,
        AlertRuleMetricType metricType,
        BigDecimal observedValue,
        BigDecimal threshold,
        AlertRuleComparisonOperator operator,
        Severity severity,
        boolean triggered,
        Instant evaluatedAt) {
}