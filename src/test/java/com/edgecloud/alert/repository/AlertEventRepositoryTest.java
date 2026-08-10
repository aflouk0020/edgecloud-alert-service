package com.edgecloud.alert.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;

import com.edgecloud.alert.dto.AlertEventFilter;
import com.edgecloud.alert.entity.AlertEvent;
import com.edgecloud.alert.entity.AlertEventSourceType;
import com.edgecloud.alert.entity.AlertEventStatus;
import com.edgecloud.alert.entity.AlertRuleComparisonOperator;
import com.edgecloud.alert.entity.AlertRuleMetricType;
import com.edgecloud.alert.entity.Severity;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class AlertEventRepositoryTest {

    @Autowired
    private AlertEventRepository repository;

    @Test
    void findsOpenEventByCompleteIdentityAndScopesDetailByProject() {
        UUID projectId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        AlertEvent event = repository.saveAndFlush(event(projectId, ruleId, "device-1",
                AlertEventStatus.OPEN, Severity.HIGH, Instant.parse("2026-08-10T10:00:00Z")));

        assertThat(repository.findByProjectIdAndAlertRuleIdAndSourceTypeAndSourceIdAndMetricTypeAndStatusIn(
                projectId, ruleId, AlertEventSourceType.DEVICE, "device-1",
                AlertRuleMetricType.CPU_USAGE, List.of(AlertEventStatus.OPEN, AlertEventStatus.ACKNOWLEDGED)))
                .contains(event);
        assertThat(repository.findByIdAndProjectId(event.getId(), projectId)).contains(event);
        assertThat(repository.findByIdAndProjectId(event.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void filtersProjectHistoryByStatusSeveritySourceAndTime() {
        UUID projectId = UUID.randomUUID();
        UUID otherProject = UUID.randomUUID();
        Instant includedAt = Instant.parse("2026-08-10T10:00:00Z");
        AlertEvent included = repository.save(event(projectId, UUID.randomUUID(), "device-1",
                AlertEventStatus.RESOLVED, Severity.HIGH, includedAt));
        repository.save(event(projectId, UUID.randomUUID(), "device-2",
                AlertEventStatus.OPEN, Severity.LOW, includedAt.plusSeconds(60)));
        repository.save(event(otherProject, UUID.randomUUID(), "device-1",
                AlertEventStatus.RESOLVED, Severity.HIGH, includedAt));
        repository.flush();

        AlertEventFilter filter = new AlertEventFilter(
                AlertEventStatus.RESOLVED, Severity.HIGH, AlertEventSourceType.DEVICE, "device-1",
                null,
                includedAt.minusSeconds(1), includedAt.plusSeconds(1));
        var page = repository.findAll(
                AlertEventSpecifications.forProject(projectId, filter),
                PageRequest.of(0, 20, Sort.by(Sort.Order.desc("triggeredAt"), Sort.Order.asc("id"))));

        assertThat(page.getContent()).containsExactly(included);
    }

    @Test
    void ownerFilterIsProjectScopedExcludesUnownedAndComposesWithExistingFilters() {
        UUID projectId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant triggeredAt = Instant.parse("2026-08-10T10:00:00Z");
        AlertEvent ownedMatch = event(projectId, UUID.randomUUID(), "device-1",
                AlertEventStatus.ACKNOWLEDGED, Severity.HIGH, triggeredAt);
        ownedMatch.setOwnerUserId(ownerId);
        repository.save(ownedMatch);
        repository.save(event(projectId, UUID.randomUUID(), "device-1",
                AlertEventStatus.OPEN, Severity.HIGH, triggeredAt.plusSeconds(1)));
        AlertEvent differentOwner = event(projectId, UUID.randomUUID(), "device-1",
                AlertEventStatus.ACKNOWLEDGED, Severity.HIGH, triggeredAt.plusSeconds(2));
        differentOwner.setOwnerUserId(UUID.randomUUID());
        repository.save(differentOwner);
        AlertEvent otherProject = event(UUID.randomUUID(), UUID.randomUUID(), "device-1",
                AlertEventStatus.ACKNOWLEDGED, Severity.HIGH, triggeredAt);
        otherProject.setOwnerUserId(ownerId);
        repository.save(otherProject);
        repository.flush();

        AlertEventFilter filter = new AlertEventFilter(
                AlertEventStatus.ACKNOWLEDGED, Severity.HIGH, AlertEventSourceType.DEVICE,
                "device-1", ownerId, triggeredAt.minusSeconds(1), triggeredAt.plusSeconds(10));
        var result = repository.findAll(AlertEventSpecifications.forProject(projectId, filter));

        assertThat(result).containsExactly(ownedMatch);
    }

    @Test
    void paginatesInDeterministicChronologicalOrderAndIncludesResolvedHistory() {
        UUID projectId = UUID.randomUUID();
        Instant base = Instant.parse("2026-08-10T10:00:00Z");
        AlertEvent oldest = repository.save(event(projectId, UUID.randomUUID(), "device-1",
                AlertEventStatus.RESOLVED, Severity.MEDIUM, base));
        AlertEvent middle = repository.save(event(projectId, UUID.randomUUID(), "device-1",
                AlertEventStatus.OPEN, Severity.MEDIUM, base.plusSeconds(60)));
        AlertEvent newest = repository.save(event(projectId, UUID.randomUUID(), "device-1",
                AlertEventStatus.RESOLVED, Severity.MEDIUM, base.plusSeconds(120)));
        repository.flush();

        var firstPage = repository.findAll(
                AlertEventSpecifications.forProject(projectId, new AlertEventFilter(null, null, null, null, null, null, null)),
                PageRequest.of(0, 2, Sort.by(Sort.Order.desc("triggeredAt"), Sort.Order.asc("id"))));
        var secondPage = repository.findAll(
                AlertEventSpecifications.forProject(projectId, null),
                PageRequest.of(1, 2, Sort.by(Sort.Order.desc("triggeredAt"), Sort.Order.asc("id"))));

        assertThat(firstPage.getContent()).containsExactly(newest, middle);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(secondPage.getContent()).containsExactly(oldest);
    }

    @Test
    void databaseConstraintRejectsSecondOpenEventForSameIdentity() {
        UUID projectId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        repository.saveAndFlush(event(projectId, ruleId, "device-1",
                AlertEventStatus.OPEN, Severity.HIGH, Instant.parse("2026-08-10T10:00:00Z")));

        assertThatThrownBy(() -> repository.saveAndFlush(event(projectId, ruleId, "device-1",
                AlertEventStatus.OPEN, Severity.HIGH, Instant.parse("2026-08-10T10:01:00Z"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseConstraintAllowsResolvedHistoryForSameIdentity() {
        UUID projectId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        repository.save(event(projectId, ruleId, "device-1",
                AlertEventStatus.RESOLVED, Severity.HIGH, Instant.parse("2026-08-10T10:00:00Z")));
        repository.save(event(projectId, ruleId, "device-1",
                AlertEventStatus.RESOLVED, Severity.HIGH, Instant.parse("2026-08-10T11:00:00Z")));
        repository.flush();

        assertThat(repository.count()).isEqualTo(2);
    }

    private AlertEvent event(UUID projectId, UUID ruleId, String sourceId, AlertEventStatus status,
                             Severity severity, Instant triggeredAt) {
        AlertEvent event = new AlertEvent();
        event.setAlertRuleId(ruleId);
        event.setAlertRuleName("CPU overload");
        event.setProjectId(projectId);
        event.setSourceType(AlertEventSourceType.DEVICE);
        event.setSourceId(sourceId);
        event.setMetricType(AlertRuleMetricType.CPU_USAGE);
        event.setObservedValue(new BigDecimal("95.25"));
        event.setThresholdValue(new BigDecimal("80.00"));
        event.setComparisonOperator(AlertRuleComparisonOperator.GREATER_THAN);
        event.setSeverity(severity);
        event.setStatus(status);
        event.setTriggeredAt(triggeredAt);
        event.setLastObservedAt(triggeredAt);
        if (status == AlertEventStatus.RESOLVED) event.setResolvedAt(triggeredAt.plusSeconds(30));
        event.setCreatedAt(triggeredAt);
        event.setUpdatedAt(triggeredAt);
        return event;
    }
}
