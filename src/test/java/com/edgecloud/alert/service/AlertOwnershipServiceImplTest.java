package com.edgecloud.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.edgecloud.alert.entity.AlertEvent;
import com.edgecloud.alert.entity.AlertEventOwnershipAction;
import com.edgecloud.alert.entity.AlertEventOwnershipHistory;
import com.edgecloud.alert.entity.AlertEventSourceType;
import com.edgecloud.alert.entity.AlertEventStatus;
import com.edgecloud.alert.entity.AlertRuleComparisonOperator;
import com.edgecloud.alert.entity.AlertRuleMetricType;
import com.edgecloud.alert.entity.Severity;
import com.edgecloud.alert.exception.AlertNotFoundException;
import com.edgecloud.alert.exception.AlertOwnershipConflictException;
import com.edgecloud.alert.exception.AlertOwnershipReleaseForbiddenException;
import com.edgecloud.alert.exception.InvalidAlertLifecycleTransitionException;
import com.edgecloud.alert.repository.AlertEventOwnershipHistoryRepository;
import com.edgecloud.alert.repository.AlertEventRepository;

@ExtendWith(MockitoExtension.class)
class AlertOwnershipServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID ALERT_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID OTHER_OWNER_ID = UUID.randomUUID();
    private static final Instant CHANGED_AT = Instant.parse("2026-08-11T10:15:30Z");

    @Mock private AlertEventRepository eventRepository;
    @Mock private AlertEventOwnershipHistoryRepository historyRepository;

    private AlertOwnershipService service;

    @BeforeEach
    void setUp() {
        service = new AlertOwnershipServiceImpl(
                eventRepository, historyRepository, Clock.fixed(CHANGED_AT, ZoneOffset.UTC));
    }

    @Test
    void openAlertIsAcknowledgedWithOwnerTimesAndHistory() {
        AlertEvent event = event(AlertEventStatus.OPEN);
        locked(event);
        when(eventRepository.save(event)).thenReturn(event);

        var response = service.acknowledge(PROJECT_ID, ALERT_ID, OWNER_ID, " engineer@example.com ");

        assertThat(response.status()).isEqualTo(AlertEventStatus.ACKNOWLEDGED);
        assertThat(response.ownerUserId()).isEqualTo(OWNER_ID);
        assertThat(response.ownerDisplayName()).isEqualTo("engineer@example.com");
        assertThat(response.acknowledgedAt()).isEqualTo(CHANGED_AT);
        assertThat(response.ownershipChangedAt()).isEqualTo(CHANGED_AT);
        ArgumentCaptor<AlertEventOwnershipHistory> history = ArgumentCaptor.forClass(AlertEventOwnershipHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo(AlertEventOwnershipAction.ACKNOWLEDGED);
        assertThat(history.getValue().getActorUserId()).isEqualTo(OWNER_ID);
        assertThat(history.getValue().getOwnerUserId()).isEqualTo(OWNER_ID);
    }

    @Test
    void sameOwnerAcknowledgementIsIdempotentWithoutSecondHistoryRow() {
        AlertEvent event = event(AlertEventStatus.ACKNOWLEDGED);
        event.setOwnerUserId(OWNER_ID);
        event.setAcknowledgedAt(CHANGED_AT.minusSeconds(60));
        event.setOwnershipChangedAt(CHANGED_AT.minusSeconds(60));
        locked(event);

        var response = service.acknowledge(PROJECT_ID, ALERT_ID, OWNER_ID, "new label");

        assertThat(response.acknowledgedAt()).isEqualTo(CHANGED_AT.minusSeconds(60));
        assertThat(response.ownershipChangedAt()).isEqualTo(CHANGED_AT.minusSeconds(60));
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(historyRepository);
    }

    @Test
    void secondOwnerCannotTakeAcknowledgedAlert() {
        AlertEvent event = event(AlertEventStatus.ACKNOWLEDGED);
        event.setOwnerUserId(OWNER_ID);
        locked(event);

        assertThatThrownBy(() -> service.acknowledge(PROJECT_ID, ALERT_ID, OTHER_OWNER_ID, null))
                .isInstanceOf(AlertOwnershipConflictException.class);
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(historyRepository);
    }

    @Test
    void currentOwnerReleasesAndClearsCurrentOwnershipWithHistory() {
        AlertEvent event = event(AlertEventStatus.ACKNOWLEDGED);
        event.setOwnerUserId(OWNER_ID);
        event.setOwnerDisplayName("engineer@example.com");
        event.setAcknowledgedAt(CHANGED_AT.minusSeconds(60));
        locked(event);
        when(eventRepository.save(event)).thenReturn(event);

        var response = service.release(PROJECT_ID, ALERT_ID, OWNER_ID);

        assertThat(response.status()).isEqualTo(AlertEventStatus.OPEN);
        assertThat(response.ownerUserId()).isNull();
        assertThat(response.ownerDisplayName()).isNull();
        assertThat(response.acknowledgedAt()).isNull();
        assertThat(response.ownershipChangedAt()).isEqualTo(CHANGED_AT);
        ArgumentCaptor<AlertEventOwnershipHistory> history = ArgumentCaptor.forClass(AlertEventOwnershipHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo(AlertEventOwnershipAction.RELEASED);
        assertThat(history.getValue().getOwnerUserId()).isEqualTo(OWNER_ID);
    }

    @Test
    void nonOwnerCannotRelease() {
        AlertEvent event = event(AlertEventStatus.ACKNOWLEDGED);
        event.setOwnerUserId(OWNER_ID);
        locked(event);

        assertThatThrownBy(() -> service.release(PROJECT_ID, ALERT_ID, OTHER_OWNER_ID))
                .isInstanceOf(AlertOwnershipReleaseForbiddenException.class);
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(historyRepository);
    }

    @Test
    void resolvedAlertCannotBeAcknowledgedOrReleased() {
        AlertEvent event = event(AlertEventStatus.RESOLVED);
        when(eventRepository.findByIdAndProjectIdForUpdate(ALERT_ID, PROJECT_ID))
                .thenReturn(Optional.of(event), Optional.of(event));

        assertThatThrownBy(() -> service.acknowledge(PROJECT_ID, ALERT_ID, OWNER_ID, null))
                .isInstanceOf(InvalidAlertLifecycleTransitionException.class);
        assertThatThrownBy(() -> service.release(PROJECT_ID, ALERT_ID, OWNER_ID))
                .isInstanceOf(InvalidAlertLifecycleTransitionException.class);
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(historyRepository);
    }

    @Test
    void openAlertCannotBeReleased() {
        locked(event(AlertEventStatus.OPEN));
        assertThatThrownBy(() -> service.release(PROJECT_ID, ALERT_ID, OWNER_ID))
                .isInstanceOf(InvalidAlertLifecycleTransitionException.class);
    }

    @Test
    void projectScopedLockPreventsCrossProjectLookup() {
        UUID foreignProject = UUID.randomUUID();
        when(eventRepository.findByIdAndProjectIdForUpdate(ALERT_ID, foreignProject)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acknowledge(foreignProject, ALERT_ID, OWNER_ID, null))
                .isInstanceOf(AlertNotFoundException.class);
        verify(eventRepository).findByIdAndProjectIdForUpdate(ALERT_ID, foreignProject);
        verifyNoInteractions(historyRepository);
    }

    @Test
    void historyVerifiesProjectAlertAndMapsDeterministicallyOrderedDtos() {
        AlertEvent event = event(AlertEventStatus.RESOLVED);
        when(eventRepository.findByIdAndProjectId(ALERT_ID, PROJECT_ID)).thenReturn(Optional.of(event));
        AlertEventOwnershipHistory history = mock(AlertEventOwnershipHistory.class);
        UUID historyId = UUID.randomUUID();
        when(history.getId()).thenReturn(historyId);
        when(history.getAlertEventId()).thenReturn(ALERT_ID);
        when(history.getProjectId()).thenReturn(PROJECT_ID);
        when(history.getActorUserId()).thenReturn(OWNER_ID);
        when(history.getOwnerUserId()).thenReturn(OWNER_ID);
        when(history.getOwnerDisplayName()).thenReturn("engineer@example.com");
        when(history.getAction()).thenReturn(AlertEventOwnershipAction.ACKNOWLEDGED);
        when(history.getChangedAt()).thenReturn(CHANGED_AT);
        when(historyRepository.findByProjectIdAndAlertEventIdOrderByChangedAtAscIdAsc(PROJECT_ID, ALERT_ID))
                .thenReturn(java.util.List.of(history));

        var result = service.history(PROJECT_ID, ALERT_ID);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(historyId);
            assertThat(item.alertId()).isEqualTo(ALERT_ID);
            assertThat(item.projectId()).isEqualTo(PROJECT_ID);
            assertThat(item.action()).isEqualTo(AlertEventOwnershipAction.ACKNOWLEDGED);
            assertThat(item.ownerUserId()).isEqualTo(OWNER_ID);
            assertThat(item.changedAt()).isEqualTo(CHANGED_AT);
        });
        verify(historyRepository).findByProjectIdAndAlertEventIdOrderByChangedAtAscIdAsc(PROJECT_ID, ALERT_ID);
    }

    @Test
    void historyDoesNotQueryAuditRowsForForeignProjectAlert() {
        when(eventRepository.findByIdAndProjectId(ALERT_ID, PROJECT_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.history(PROJECT_ID, ALERT_ID))
                .isInstanceOf(AlertNotFoundException.class);
        verifyNoInteractions(historyRepository);
    }

    private void locked(AlertEvent event) {
        when(eventRepository.findByIdAndProjectIdForUpdate(ALERT_ID, PROJECT_ID)).thenReturn(Optional.of(event));
    }

    private AlertEvent event(AlertEventStatus status) {
        AlertEvent event = new AlertEvent();
        event.setAlertRuleId(UUID.randomUUID());
        event.setAlertRuleName("CPU overload");
        event.setProjectId(PROJECT_ID);
        event.setSourceType(AlertEventSourceType.DEVICE);
        event.setSourceId("device-1");
        event.setMetricType(AlertRuleMetricType.CPU_USAGE);
        event.setObservedValue(new BigDecimal("95.25"));
        event.setThresholdValue(new BigDecimal("80.00"));
        event.setComparisonOperator(AlertRuleComparisonOperator.GREATER_THAN);
        event.setSeverity(Severity.HIGH);
        event.setStatus(status);
        event.setTriggeredAt(CHANGED_AT.minusSeconds(300));
        event.setLastObservedAt(CHANGED_AT.minusSeconds(300));
        event.setCreatedAt(CHANGED_AT.minusSeconds(300));
        event.setUpdatedAt(CHANGED_AT.minusSeconds(300));
        return event;
    }
}
