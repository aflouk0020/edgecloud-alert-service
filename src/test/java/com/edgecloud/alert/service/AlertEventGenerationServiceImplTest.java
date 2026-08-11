package com.edgecloud.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.edgecloud.alert.entity.AlertEvent;
import com.edgecloud.alert.entity.AlertEventSourceType;
import com.edgecloud.alert.entity.AlertEventStatus;
import com.edgecloud.alert.entity.AlertRuleComparisonOperator;
import com.edgecloud.alert.entity.AlertRuleMetricType;
import com.edgecloud.alert.entity.Severity;
import com.edgecloud.alert.entity.NotificationLifecycleEventType;
import com.edgecloud.alert.evaluation.AlertEvaluationResult;
import com.edgecloud.alert.evaluation.AlertEvaluationSourceType;
import com.edgecloud.alert.exception.AlertEvaluationValidationException;
import com.edgecloud.alert.repository.AlertEventRepository;

@ExtendWith(MockitoExtension.class)
class AlertEventGenerationServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID RULE_ID = UUID.randomUUID();
    private static final Instant FIRST_OBSERVED = Instant.parse("2026-08-10T10:00:00Z");

    @Mock
    private AlertEventRepository repository;
    @Mock
    private AlertNotificationOutboxService outboxService;

    private AlertEventGenerationService service;

    @BeforeEach
    void setUp() {
        service = new AlertEventGenerationServiceImpl(repository, outboxService);
    }

    @Test
    void triggeredResultCreatesOpenEventWithEvidenceAndDeviceSource() {
        AlertEvaluationResult result = result(true, AlertEvaluationSourceType.DEVICE, "device-1", FIRST_OBSERVED);
        AlertEvent stored = event(AlertEventStatus.OPEN, "device-1", FIRST_OBSERVED);
        AtomicReference<UUID> insertedId = new AtomicReference<>();
        when(repository.upsertOpen(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString(),
                anyString(), org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
                    insertedId.set(UUID.fromString(invocation.getArgument(0)));
                    return 1;
                });
        when(repository.findActiveForUpdate(PROJECT_ID, RULE_ID, AlertEventSourceType.DEVICE, "device-1",
                AlertRuleMetricType.CPU_USAGE)).thenAnswer(invocation -> {
                    ReflectionTestUtils.setField(stored, "id", insertedId.get());
                    return Optional.of(stored);
                });

        var response = service.process(result);

        assertThat(response).isPresent();
        assertThat(response.orElseThrow().alertRuleName()).isEqualTo("CPU overload");
        assertThat(response.orElseThrow().observedValue()).isEqualByComparingTo("95.25");
        assertThat(response.orElseThrow().thresholdValue()).isEqualByComparingTo("80.00");
        assertThat(response.orElseThrow().severity()).isEqualTo(Severity.HIGH);
        assertThat(response.orElseThrow().sourceType()).isEqualTo(AlertEventSourceType.DEVICE);
        verify(repository).upsertOpen(
                anyString(), eq(RULE_ID.toString()), eq("CPU overload"), eq(PROJECT_ID.toString()),
                eq("DEVICE"), eq("device-1"), eq("CPU_USAGE"), eq(new BigDecimal("95.25")),
                eq(new BigDecimal("80.00")), eq("GREATER_THAN"), eq("HIGH"), eq(FIRST_OBSERVED));
        verify(outboxService).enqueue(stored, NotificationLifecycleEventType.OPENED, FIRST_OBSERVED);
    }

    @Test
    void suppressedTriggeredConditionCreatesNoAlertOrNotificationOutbox() {
        AlertSuppressionService suppression = mock(AlertSuppressionService.class);
        AlertEvaluationResult result = result(true, AlertEvaluationSourceType.DEVICE, "device-1", FIRST_OBSERVED);
        when(suppression.suppressMatching(result)).thenReturn(1);
        var suppressedService = new AlertEventGenerationServiceImpl(repository, outboxService, suppression);
        assertThat(suppressedService.process(result)).isEmpty();
        verifyNoInteractions(repository, outboxService);
    }

    @Test
    void repeatedTriggerUsesAtomicUpsertAndUpdatesLastObservation() {
        Instant later = FIRST_OBSERVED.plusSeconds(60);
        when(repository.findActiveForUpdate(eq(PROJECT_ID), eq(RULE_ID), eq(AlertEventSourceType.DEVICE),
                eq("device-1"), eq(AlertRuleMetricType.CPU_USAGE)))
                .thenReturn(Optional.of(event(AlertEventStatus.OPEN, "device-1", FIRST_OBSERVED)))
                .thenReturn(Optional.of(event(AlertEventStatus.OPEN, "device-1", later)));

        service.process(result(true, AlertEvaluationSourceType.DEVICE, "device-1", FIRST_OBSERVED));
        var updated = service.process(result(true, AlertEvaluationSourceType.DEVICE, "device-1", later));

        assertThat(updated.orElseThrow().lastObservedAt()).isEqualTo(later);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(repository, org.mockito.Mockito.times(2)).upsertOpen(
                anyString(), eq(RULE_ID.toString()), eq("CPU overload"), eq(PROJECT_ID.toString()),
                eq("DEVICE"), eq("device-1"), eq("CPU_USAGE"), eq(new BigDecimal("95.25")),
                eq(new BigDecimal("80.00")), eq("GREATER_THAN"), eq("HIGH"),
                org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(outboxService);
    }

    @Test
    void nonTriggeredWithoutOpenEventIsNoOp() {
        AlertEvaluationResult result = result(false, AlertEvaluationSourceType.DEVICE, "device-1", FIRST_OBSERVED);
        when(repository.findActiveForUpdate(PROJECT_ID, RULE_ID, AlertEventSourceType.DEVICE,
                "device-1", AlertRuleMetricType.CPU_USAGE)).thenReturn(Optional.empty());

        assertThat(service.process(result)).isEmpty();

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(repository, never()).upsertOpen(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recoveryResolvesOpenEventAndSetsLifecycleTimes() {
        Instant recoveredAt = FIRST_OBSERVED.plusSeconds(120);
        AlertEvent open = event(AlertEventStatus.OPEN, "device-1", FIRST_OBSERVED);
        when(repository.findActiveForUpdate(PROJECT_ID, RULE_ID, AlertEventSourceType.DEVICE,
                "device-1", AlertRuleMetricType.CPU_USAGE)).thenReturn(Optional.of(open));
        when(repository.save(open)).thenReturn(open);

        var response = service.process(result(false, AlertEvaluationSourceType.DEVICE, "device-1", recoveredAt));

        assertThat(response.orElseThrow().status()).isEqualTo(AlertEventStatus.RESOLVED);
        assertThat(open.getResolvedAt()).isEqualTo(recoveredAt);
        assertThat(open.getLastObservedAt()).isEqualTo(recoveredAt);
        assertThat(open.getUpdatedAt()).isEqualTo(recoveredAt);
        verify(outboxService).enqueue(open, NotificationLifecycleEventType.RESOLVED, recoveredAt);
    }

    @Test
    void recoveryResolvesAcknowledgedEventAndPreservesOwnershipEvidence() {
        Instant acknowledgedAt = FIRST_OBSERVED.plusSeconds(30);
        Instant recoveredAt = FIRST_OBSERVED.plusSeconds(120);
        UUID ownerId = UUID.randomUUID();
        AlertEvent acknowledged = event(AlertEventStatus.ACKNOWLEDGED, "device-1", FIRST_OBSERVED);
        acknowledged.setOwnerUserId(ownerId);
        acknowledged.setOwnerDisplayName("engineer@example.com");
        acknowledged.setAcknowledgedAt(acknowledgedAt);
        acknowledged.setOwnershipChangedAt(acknowledgedAt);
        when(repository.findActiveForUpdate(PROJECT_ID, RULE_ID, AlertEventSourceType.DEVICE,
                "device-1", AlertRuleMetricType.CPU_USAGE)).thenReturn(Optional.of(acknowledged));
        when(repository.save(acknowledged)).thenReturn(acknowledged);

        var response = service.process(result(false, AlertEvaluationSourceType.DEVICE, "device-1", recoveredAt));

        assertThat(response.orElseThrow().status()).isEqualTo(AlertEventStatus.RESOLVED);
        assertThat(response.orElseThrow().ownerUserId()).isEqualTo(ownerId);
        assertThat(response.orElseThrow().ownerDisplayName()).isEqualTo("engineer@example.com");
        assertThat(response.orElseThrow().acknowledgedAt()).isEqualTo(acknowledgedAt);
        assertThat(response.orElseThrow().ownershipChangedAt()).isEqualTo(acknowledgedAt);
    }

    @Test
    void triggerWhileAcknowledgedReturnsSameActiveOwnedEvent() {
        UUID ownerId = UUID.randomUUID();
        AlertEvent acknowledged = event(AlertEventStatus.ACKNOWLEDGED, "device-1", FIRST_OBSERVED);
        acknowledged.setOwnerUserId(ownerId);
        acknowledged.setAcknowledgedAt(FIRST_OBSERVED);
        when(repository.findActiveForUpdate(PROJECT_ID, RULE_ID, AlertEventSourceType.DEVICE, "device-1",
                AlertRuleMetricType.CPU_USAGE)).thenReturn(Optional.of(acknowledged));

        var response = service.process(result(true, AlertEvaluationSourceType.DEVICE, "device-1",
                FIRST_OBSERVED.plusSeconds(60))).orElseThrow();

        assertThat(response.status()).isEqualTo(AlertEventStatus.ACKNOWLEDGED);
        assertThat(response.ownerUserId()).isEqualTo(ownerId);
        verify(repository).upsertOpen(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void retriggerAfterResolutionCreatesNewOpenAndPreservesHistory() {
        AlertEvent historical = event(AlertEventStatus.OPEN, "device-1", FIRST_OBSERVED);
        Instant resolvedAt = FIRST_OBSERVED.plusSeconds(60);
        when(repository.findActiveForUpdate(PROJECT_ID, RULE_ID, AlertEventSourceType.DEVICE,
                "device-1", AlertRuleMetricType.CPU_USAGE)).thenReturn(Optional.of(historical));
        when(repository.save(historical)).thenReturn(historical);
        service.process(result(false, AlertEvaluationSourceType.DEVICE, "device-1", resolvedAt));

        Instant retriggeredAt = resolvedAt.plusSeconds(60);
        AlertEvent newOpen = event(AlertEventStatus.OPEN, "device-1", retriggeredAt);
        when(repository.findActiveForUpdate(PROJECT_ID, RULE_ID, AlertEventSourceType.DEVICE,
                "device-1", AlertRuleMetricType.CPU_USAGE)).thenReturn(Optional.of(newOpen));

        var response = service.process(result(true, AlertEvaluationSourceType.DEVICE, "device-1", retriggeredAt));

        assertThat(response.orElseThrow().status()).isEqualTo(AlertEventStatus.OPEN);
        assertThat(historical.getStatus()).isEqualTo(AlertEventStatus.RESOLVED);
        assertThat(historical.getResolvedAt()).isEqualTo(resolvedAt);
    }

    @Test
    void mapsServiceSource() {
        String serviceId = UUID.randomUUID().toString();
        AlertEvent stored = event(AlertEventStatus.OPEN, serviceId, FIRST_OBSERVED);
        stored.setSourceType(AlertEventSourceType.SERVICE);
        when(repository.findActiveForUpdate(PROJECT_ID, RULE_ID, AlertEventSourceType.SERVICE, serviceId,
                AlertRuleMetricType.CPU_USAGE)).thenReturn(Optional.of(stored));

        assertThat(service.process(result(true, AlertEvaluationSourceType.SERVICE, serviceId, FIRST_OBSERVED)))
                .get().extracting(response -> response.sourceType()).isEqualTo(AlertEventSourceType.SERVICE);
    }

    @Test
    void rejectsIncompleteOrMalformedResultBeforePersistence() {
        AlertEvaluationResult missingRuleName = new AlertEvaluationResult(
                RULE_ID, " ", PROJECT_ID, AlertEvaluationSourceType.DEVICE, "device-1",
                AlertRuleMetricType.CPU_USAGE, BigDecimal.ONE, BigDecimal.TEN,
                AlertRuleComparisonOperator.GREATER_THAN, Severity.HIGH, true, FIRST_OBSERVED);
        AlertEvaluationResult malformedService = result(true, AlertEvaluationSourceType.SERVICE, "not-a-uuid", FIRST_OBSERVED);

        assertThatThrownBy(() -> service.process(missingRuleName)).isInstanceOf(AlertEvaluationValidationException.class);
        assertThatThrownBy(() -> service.process(malformedService)).isInstanceOf(AlertEvaluationValidationException.class);
        verifyNoInteractions(repository);
    }

    private AlertEvaluationResult result(boolean triggered, AlertEvaluationSourceType sourceType,
                                         String sourceId, Instant evaluatedAt) {
        return new AlertEvaluationResult(
                RULE_ID, "CPU overload", PROJECT_ID, sourceType, sourceId, AlertRuleMetricType.CPU_USAGE,
                new BigDecimal("95.25"), new BigDecimal("80.00"),
                AlertRuleComparisonOperator.GREATER_THAN, Severity.HIGH, triggered, evaluatedAt);
    }

    private AlertEvent event(AlertEventStatus status, String sourceId, Instant observedAt) {
        AlertEvent event = new AlertEvent();
        event.setAlertRuleId(RULE_ID);
        event.setAlertRuleName("CPU overload");
        event.setProjectId(PROJECT_ID);
        event.setSourceType(AlertEventSourceType.DEVICE);
        event.setSourceId(sourceId);
        event.setMetricType(AlertRuleMetricType.CPU_USAGE);
        event.setObservedValue(new BigDecimal("95.25"));
        event.setThresholdValue(new BigDecimal("80.00"));
        event.setComparisonOperator(AlertRuleComparisonOperator.GREATER_THAN);
        event.setSeverity(Severity.HIGH);
        event.setStatus(status);
        event.setTriggeredAt(observedAt);
        event.setLastObservedAt(observedAt);
        event.setCreatedAt(observedAt);
        event.setUpdatedAt(observedAt);
        return event;
    }
}
