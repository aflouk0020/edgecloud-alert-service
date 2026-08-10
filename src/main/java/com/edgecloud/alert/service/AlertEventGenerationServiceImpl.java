package com.edgecloud.alert.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edgecloud.alert.dto.AlertEventResponse;
import com.edgecloud.alert.entity.AlertEventSourceType;
import com.edgecloud.alert.evaluation.AlertEvaluationResult;
import com.edgecloud.alert.exception.AlertEvaluationValidationException;
import com.edgecloud.alert.repository.AlertEventRepository;

@Service
public class AlertEventGenerationServiceImpl implements AlertEventGenerationService {

    private final AlertEventRepository repository;

    public AlertEventGenerationServiceImpl(AlertEventRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Optional<AlertEventResponse> process(AlertEvaluationResult result) {
        validate(result);
        AlertEventSourceType sourceType = AlertEventSourceType.valueOf(result.sourceType().name());
        String sourceId = result.sourceId().trim();

        if (result.triggered()) {
            repository.upsertOpen(
                    UUID.randomUUID().toString(),
                    result.ruleId().toString(),
                    result.ruleName().trim(),
                    result.projectId().toString(),
                    sourceType.name(),
                    sourceId,
                    result.metricType().name(),
                    result.observedValue(),
                    result.threshold(),
                    result.operator().name(),
                    result.severity().name(),
                    result.evaluatedAt());
            AlertEventResponse persisted = repository.findByProjectIdAndAlertRuleIdAndSourceTypeAndSourceIdAndMetricTypeAndStatus(
                            result.projectId(), result.ruleId(), sourceType, sourceId,
                            result.metricType(), com.edgecloud.alert.entity.AlertEventStatus.OPEN)
                    .map(AlertEventResponse::from)
                    .orElseThrow(() -> new IllegalStateException("OPEN alert event was not available after upsert"));
            return Optional.of(persisted);
        }

        return repository.findOpenForUpdate(
                        result.projectId(), result.ruleId(), sourceType, sourceId, result.metricType())
                .map(event -> {
                    event.resolve(result.evaluatedAt());
                    return AlertEventResponse.from(repository.save(event));
                });
    }

    private void validate(AlertEvaluationResult result) {
        if (result == null || result.ruleId() == null || result.ruleName() == null || result.ruleName().isBlank()
                || result.projectId() == null || result.sourceType() == null || result.sourceId() == null
                || result.sourceId().isBlank() || result.metricType() == null || result.observedValue() == null
                || result.threshold() == null || result.operator() == null || result.severity() == null
                || result.evaluatedAt() == null) {
            throw new AlertEvaluationValidationException("Evaluation result is incomplete");
        }
        if (result.ruleName().trim().length() > 200) {
            throw new AlertEvaluationValidationException("Evaluation ruleName exceeds 200 characters");
        }
        if (result.sourceId().trim().length() > 128) {
            throw new AlertEvaluationValidationException("Evaluation sourceId exceeds 128 characters");
        }
        if (result.sourceType() == com.edgecloud.alert.evaluation.AlertEvaluationSourceType.SERVICE) {
            try {
                UUID.fromString(result.sourceId().trim());
            } catch (IllegalArgumentException ex) {
                throw new AlertEvaluationValidationException("Service sourceId must be a valid UUID");
            }
        }
    }
}
