package com.edgecloud.alert.evaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.edgecloud.alert.entity.AlertRuleMetricType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AlertEvaluationInput(
        @NotNull UUID projectId,
        @NotNull AlertEvaluationSourceType sourceType,
        @NotBlank String sourceId,
        @NotNull AlertRuleMetricType metricType,
        @NotNull BigDecimal observedValue,
        @NotNull Instant observedAt,
        @NotBlank String sampleId) {
}