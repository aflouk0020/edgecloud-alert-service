package com.edgecloud.alert.evaluation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edgecloud.alert.entity.AlertRule;
import com.edgecloud.alert.exception.AlertEvaluationValidationException;
import com.edgecloud.alert.repository.AlertRuleRepository;

@Service
@Transactional(readOnly = true)
public class AlertRuleEvaluationServiceImpl implements AlertRuleEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AlertRuleEvaluationServiceImpl.class);

    private final AlertRuleRepository ruleRepository;
    private final ThresholdComparisonService comparisonService;
    private final RuleMatchingService matchingService;
    private final EvaluationDuplicateGuard duplicateGuard;

    public AlertRuleEvaluationServiceImpl(AlertRuleRepository ruleRepository,
                                          ThresholdComparisonService comparisonService,
                                          RuleMatchingService matchingService,
                                          EvaluationDuplicateGuard duplicateGuard) {
        this.ruleRepository = ruleRepository;
        this.comparisonService = comparisonService;
        this.matchingService = matchingService;
        this.duplicateGuard = duplicateGuard;
    }

    @Override
    public AlertEvaluationResponse evaluate(AlertEvaluationInput input) {
        validateInput(input);
        Instant evaluatedAt = Instant.now();
        String sourceId = input.sourceId().trim();
        AlertEvaluationInput normalized = new AlertEvaluationInput(
                input.projectId(), input.sourceType(), sourceId, input.metricType(),
                input.observedValue(), input.observedAt(), input.sampleId().trim());
        long started = System.nanoTime();
        log.info("Alert rule evaluation started projectId={} sourceType={} metricType={} sampleId={}",
                normalized.projectId(), normalized.sourceType(), normalized.metricType(), normalized.sampleId());

        if (duplicateGuard.isDuplicate(normalized)) {
            log.info("Alert rule evaluation duplicate skipped projectId={} sourceType={} metricType={} sampleId={}",
                    normalized.projectId(), normalized.sourceType(), normalized.metricType(), normalized.sampleId());
            return new AlertEvaluationResponse(evaluatedAt, true, 0, 0, 0, List.of());
        }

        List<AlertRule> candidates = ruleRepository.findEnabledByProjectIdAndMetricType(
                normalized.projectId(), normalized.metricType());
        log.info("Alert rule evaluation candidates projectId={} metricType={} candidateCount={}",
                normalized.projectId(), normalized.metricType(), candidates.size());

        List<AlertEvaluationResult> results = candidates.stream()
                .filter(rule -> evaluateRule(rule, normalized))
                .map(rule -> toResult(rule, normalized, evaluatedAt))
                .toList();

        long triggeredCount = results.stream().filter(AlertEvaluationResult::triggered).count();
        log.info("Alert rule evaluation completed projectId={} evaluatedCount={} triggeredCount={} durationMs={}",
                normalized.projectId(), results.size(), triggeredCount,
                Duration.ofNanos(System.nanoTime() - started).toMillis());
        return new AlertEvaluationResponse(evaluatedAt, false, candidates.size(), results.size(),
                Math.toIntExact(triggeredCount), results);
    }

    private boolean evaluateRule(AlertRule rule, AlertEvaluationInput input) {
        if (!matchingService.isValid(rule)) {
            log.warn("Invalid alert rule skipped ruleId={} projectId={}", rule == null ? null : rule.getId(), input.projectId());
            return false;
        }
        if (!matchingService.matches(rule, input)) {
            return false;
        }
        return true;
    }

    private AlertEvaluationResult toResult(AlertRule rule, AlertEvaluationInput input, Instant evaluatedAt) {
        boolean triggered = comparisonService.compare(
                input.observedValue(), rule.getThresholdValue(), rule.getComparisonOperator());
        return new AlertEvaluationResult(
                rule.getId(), rule.getName(), input.projectId(), input.sourceType(), input.sourceId(), input.metricType(),
                input.observedValue(), rule.getThresholdValue(), rule.getComparisonOperator(),
                rule.getSeverity(), triggered, evaluatedAt);
    }

    private void validateInput(AlertEvaluationInput input) {
        if (input == null || input.projectId() == null || input.sourceType() == null
                || input.metricType() == null || input.observedValue() == null
                || input.observedAt() == null || input.sampleId() == null || input.sampleId().isBlank()
                || input.sourceId() == null || input.sourceId().isBlank()) {
            throw new AlertEvaluationValidationException("Evaluation input is incomplete");
        }
        if (!input.observedValue().toString().equals("NaN") && !input.observedValue().toString().equals("Infinity")) {
            if (input.sourceType() == AlertEvaluationSourceType.SERVICE) {
                try {
                    java.util.UUID.fromString(input.sourceId().trim());
                } catch (IllegalArgumentException ex) {
                    throw new AlertEvaluationValidationException("Service sourceId must be a valid UUID");
                }
            }
        }
    }
}
