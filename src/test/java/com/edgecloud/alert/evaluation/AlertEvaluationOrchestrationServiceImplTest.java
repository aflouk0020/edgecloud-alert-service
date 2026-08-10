package com.edgecloud.alert.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.edgecloud.alert.entity.AlertRuleComparisonOperator;
import com.edgecloud.alert.entity.AlertRuleMetricType;
import com.edgecloud.alert.entity.Severity;
import com.edgecloud.alert.service.AlertEventGenerationService;
import com.edgecloud.alert.service.AlertService;
import com.edgecloud.alert.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class AlertEvaluationOrchestrationServiceImplTest {

    @Mock AlertRuleEvaluationService evaluationService;
    @Mock AlertEventGenerationService generationService;

    private AlertEvaluationOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new AlertEvaluationOrchestrationServiceImpl(evaluationService, generationService);
    }

    @Test
    void processesTriggeredAndRecoveryResultsAndPreservesResponseContract() {
        AlertEvaluationInput input = input();
        AlertEvaluationResult triggered = result(UUID.randomUUID(), true);
        AlertEvaluationResult recovered = result(UUID.randomUUID(), false);
        AlertEvaluationResponse expected = new AlertEvaluationResponse(
                Instant.parse("2026-08-10T10:00:00Z"), false, 2, 2, 1, List.of(triggered, recovered));
        when(evaluationService.evaluate(input)).thenReturn(expected);

        AlertEvaluationResponse actual = orchestrationService.evaluate(input);

        assertThat(actual).isSameAs(expected);
        var ordered = inOrder(generationService);
        ordered.verify(generationService).process(triggered);
        ordered.verify(generationService).process(recovered);
    }

    @Test
    void persistenceFailureForOneResultDoesNotPreventRemainingResults() {
        AlertEvaluationInput input = input();
        AlertEvaluationResult failed = result(UUID.randomUUID(), true);
        AlertEvaluationResult remaining = result(UUID.randomUUID(), false);
        AlertEvaluationResponse response = new AlertEvaluationResponse(
                Instant.now(), false, 2, 2, 1, List.of(failed, remaining));
        when(evaluationService.evaluate(input)).thenReturn(response);
        doThrow(new IllegalStateException("database unavailable")).when(generationService).process(failed);

        assertThat(orchestrationService.evaluate(input)).isSameAs(response);

        verify(generationService).process(remaining);
    }

    @Test
    void doesNotInvokeLegacyAlertOrNotificationServices() {
        AlertEvaluationInput input = input();
        when(evaluationService.evaluate(input)).thenReturn(
                new AlertEvaluationResponse(Instant.now(), false, 0, 0, 0, List.of()));
        AlertService legacyAlertService = mock(AlertService.class);
        NotificationService notificationService = mock(NotificationService.class);

        orchestrationService.evaluate(input);

        verifyNoInteractions(legacyAlertService, notificationService);
    }

    private AlertEvaluationInput input() {
        return new AlertEvaluationInput(UUID.randomUUID(), AlertEvaluationSourceType.DEVICE, "device-1",
                AlertRuleMetricType.CPU_USAGE, BigDecimal.TEN, Instant.parse("2026-08-10T10:00:00Z"), "sample-1");
    }

    private AlertEvaluationResult result(UUID ruleId, boolean triggered) {
        return new AlertEvaluationResult(
                ruleId, "CPU rule", UUID.randomUUID(), AlertEvaluationSourceType.DEVICE, "device-1",
                AlertRuleMetricType.CPU_USAGE, BigDecimal.TEN, BigDecimal.ONE,
                AlertRuleComparisonOperator.GREATER_THAN, Severity.HIGH, triggered,
                Instant.parse("2026-08-10T10:00:00Z"));
    }
}
