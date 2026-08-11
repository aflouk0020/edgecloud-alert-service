package com.edgecloud.alert.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edgecloud.alert.dto.AlertEventResponse;
import com.edgecloud.alert.entity.AlertEventSourceType;
import com.edgecloud.alert.entity.NotificationLifecycleEventType;
import com.edgecloud.alert.evaluation.AlertEvaluationResult;
import com.edgecloud.alert.exception.AlertEvaluationValidationException;
import com.edgecloud.alert.repository.AlertEventRepository;

@Service
public class AlertEventGenerationServiceImpl implements AlertEventGenerationService {

    private final AlertEventRepository repository;
    private final AlertNotificationOutboxService outboxService;

    public AlertEventGenerationServiceImpl(AlertEventRepository repository,
                                           AlertNotificationOutboxService outboxService) {
        this.repository = repository;
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public Optional<AlertEventResponse> process(AlertEvaluationResult result) {
        validate(result);
        AlertEventSourceType sourceType = AlertEventSourceType.valueOf(result.sourceType().name());
        String sourceId = result.sourceId().trim();

        if (result.triggered()) {
            UUID candidateId = UUID.randomUUID();
            repository.upsertOpen(
                    candidateId.toString(),
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
            var persistedEvent = repository.findByProjectIdAndAlertRuleIdAndSourceTypeAndSourceIdAndMetricTypeAndStatusIn(
                            result.projectId(), result.ruleId(), sourceType, sourceId,
                            result.metricType(), List.of(
                                    com.edgecloud.alert.entity.AlertEventStatus.OPEN,
                                    com.edgecloud.alert.entity.AlertEventStatus.ACKNOWLEDGED))
                    .orElseThrow(() -> new IllegalStateException("Active alert event was not available after upsert"));
            if (candidateId.equals(persistedEvent.getId())) {
                outboxService.enqueue(persistedEvent, NotificationLifecycleEventType.OPENED,
                        persistedEvent.getTriggeredAt());
            }
            return Optional.of(AlertEventResponse.from(persistedEvent));
        }

        return repository.findActiveForUpdate(
                        result.projectId(), result.ruleId(), sourceType, sourceId, result.metricType())
                .map(event -> {
                    event.resolve(result.evaluatedAt());
                    var saved = repository.save(event);
                    outboxService.enqueue(saved, NotificationLifecycleEventType.RESOLVED, saved.getResolvedAt());
                    return AlertEventResponse.from(saved);
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
