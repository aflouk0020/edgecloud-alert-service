package com.edgecloud.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.edgecloud.alert.dto.AlertEventFilter;
import com.edgecloud.alert.entity.AlertEvent;
import com.edgecloud.alert.entity.AlertEventSourceType;
import com.edgecloud.alert.entity.AlertEventStatus;
import com.edgecloud.alert.entity.AlertRuleComparisonOperator;
import com.edgecloud.alert.entity.AlertRuleMetricType;
import com.edgecloud.alert.entity.Severity;
import com.edgecloud.alert.exception.AlertEventValidationException;
import com.edgecloud.alert.exception.AlertNotFoundException;
import com.edgecloud.alert.repository.AlertEventRepository;

@ExtendWith(MockitoExtension.class)
class AlertEventQueryServiceImplTest {

    @Mock AlertEventRepository repository;

    private AlertEventQueryService service;

    @BeforeEach
    void setUp() {
        service = new AlertEventQueryServiceImpl(repository);
    }

    @Test
    void appliesDefaultPaginationAndDeterministicOrdering() {
        AlertEvent storedEvent = event(UUID.randomUUID());
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(storedEvent)));

        var response = service.list(UUID.randomUUID(), null, null, null, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("triggeredAt").isDescending()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("id").isAscending()).isTrue();
        assertThat(response.alerts()).hasSize(1);
    }

    @Test
    void acceptsMaximumPageSizeAndAscendingDirection() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(UUID.randomUUID(), new AlertEventFilter(
                AlertEventStatus.OPEN, Severity.HIGH, AlertEventSourceType.DEVICE, "device-1",
                Instant.parse("2026-08-10T09:00:00Z"), Instant.parse("2026-08-10T10:00:00Z")),
                2, 100, "ASC");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().getOrderFor("triggeredAt").isAscending()).isTrue();
    }

    @Test
    void rejectsInvalidDateRangePaginationSourceAndSort() {
        UUID projectId = UUID.randomUUID();
        assertThatThrownBy(() -> service.list(projectId, null, -1, 20, "DESC"))
                .isInstanceOf(AlertEventValidationException.class);
        assertThatThrownBy(() -> service.list(projectId, null, 0, 0, "DESC"))
                .isInstanceOf(AlertEventValidationException.class);
        assertThatThrownBy(() -> service.list(projectId, null, 0, 101, "DESC"))
                .isInstanceOf(AlertEventValidationException.class);
        assertThatThrownBy(() -> service.list(projectId, null, 0, 20, "SIDEWAYS"))
                .isInstanceOf(AlertEventValidationException.class);
        assertThatThrownBy(() -> service.list(projectId,
                new AlertEventFilter(null, null, null, " ", null, null), 0, 20, "DESC"))
                .isInstanceOf(AlertEventValidationException.class);
        assertThatThrownBy(() -> service.list(projectId,
                new AlertEventFilter(null, null, null, null,
                        Instant.parse("2026-08-10T11:00:00Z"), Instant.parse("2026-08-10T10:00:00Z")),
                0, 20, "DESC")).isInstanceOf(AlertEventValidationException.class);
    }

    @Test
    void retrievesDetailOnlyInsideRequestedProject() {
        UUID projectId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        AlertEvent event = event(alertId);
        when(repository.findByIdAndProjectId(alertId, projectId)).thenReturn(Optional.of(event));

        assertThat(service.get(projectId, alertId).alertId()).isEqualTo(alertId);
        verify(repository).findByIdAndProjectId(alertId, projectId);
    }

    @Test
    void crossProjectDetailIsNotReturned() {
        UUID alertId = UUID.randomUUID();
        UUID requestedProject = UUID.randomUUID();
        when(repository.findByIdAndProjectId(alertId, requestedProject)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(requestedProject, alertId))
                .isInstanceOf(AlertNotFoundException.class)
                .hasMessage("Alert event not found");
    }

    private AlertEvent event(UUID id) {
        AlertEvent event = org.mockito.Mockito.mock(AlertEvent.class);
        when(event.getId()).thenReturn(id);
        when(event.getAlertRuleId()).thenReturn(UUID.randomUUID());
        when(event.getAlertRuleName()).thenReturn("CPU rule");
        when(event.getProjectId()).thenReturn(UUID.randomUUID());
        when(event.getSourceType()).thenReturn(AlertEventSourceType.DEVICE);
        when(event.getSourceId()).thenReturn("device-1");
        when(event.getMetricType()).thenReturn(AlertRuleMetricType.CPU_USAGE);
        when(event.getObservedValue()).thenReturn(BigDecimal.TEN);
        when(event.getThresholdValue()).thenReturn(BigDecimal.ONE);
        when(event.getComparisonOperator()).thenReturn(AlertRuleComparisonOperator.GREATER_THAN);
        when(event.getSeverity()).thenReturn(Severity.HIGH);
        when(event.getStatus()).thenReturn(AlertEventStatus.OPEN);
        when(event.getTriggeredAt()).thenReturn(Instant.parse("2026-08-10T10:00:00Z"));
        when(event.getLastObservedAt()).thenReturn(Instant.parse("2026-08-10T10:00:00Z"));
        when(event.getCreatedAt()).thenReturn(Instant.parse("2026-08-10T10:00:00Z"));
        when(event.getUpdatedAt()).thenReturn(Instant.parse("2026-08-10T10:00:00Z"));
        return event;
    }
}
