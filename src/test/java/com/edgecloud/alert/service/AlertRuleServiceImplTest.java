package com.edgecloud.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

import com.edgecloud.alert.dto.AlertRuleRequest;
import com.edgecloud.alert.dto.AlertRuleResponse;
import com.edgecloud.alert.entity.AlertRule;
import com.edgecloud.alert.entity.AlertRuleComparisonOperator;
import com.edgecloud.alert.entity.AlertRuleMetricType;
import com.edgecloud.alert.entity.Severity;
import com.edgecloud.alert.exception.AlertNotFoundException;
import com.edgecloud.alert.exception.AlertRuleValidationException;
import com.edgecloud.alert.repository.AlertRuleRepository;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID RULE_ID = UUID.randomUUID();

    @Mock
    private AlertRuleRepository repository;

    private AlertRuleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AlertRuleServiceImpl(repository, new AlertRuleValidator());
    }

    @Test
    void createsProjectWideRule() {
        AlertRuleRequest request = request("  CPU rule  ", null, null, true);
        when(repository.save(any(AlertRule.class))).thenAnswer(invocation -> saved(invocation.getArgument(0)));

        AlertRuleResponse response = service.create(PROJECT_ID, request);

        assertThat(response.projectId()).isEqualTo(PROJECT_ID);
        assertThat(response.name()).isEqualTo("CPU rule");
        assertThat(response.deviceId()).isNull();
        assertThat(response.serviceId()).isNull();
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void createsDeviceAndServiceTargetRules() {
        when(repository.save(any(AlertRule.class))).thenAnswer(invocation -> saved(invocation.getArgument(0)));

        AlertRuleResponse deviceRule = service.create(PROJECT_ID, request("Device", "device-1", null, true));
        AlertRuleResponse serviceRule = service.create(PROJECT_ID, request("Service", null, UUID.randomUUID(), true));

        assertThat(deviceRule.deviceId()).isEqualTo("device-1");
        assertThat(serviceRule.serviceId()).isNotNull();
    }

    @Test
    void listsRulesInRepositoryOrder() {
        AlertRule first = rule("first", Instant.parse("2026-08-10T10:00:00Z"));
        AlertRule second = rule("second", Instant.parse("2026-08-10T09:00:00Z"));
        when(repository.findByProjectIdOrderByUpdatedAtDescIdAsc(PROJECT_ID)).thenReturn(List.of(first, second));

        assertThat(service.listByProject(PROJECT_ID)).extracting(AlertRuleResponse::name)
                .containsExactly("first", "second");
        verify(repository).findByProjectIdOrderByUpdatedAtDescIdAsc(PROJECT_ID);
    }

    @Test
    void retrievesOnlyWithinRequestedProject() {
        when(repository.findByIdAndProjectId(RULE_ID, PROJECT_ID)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.get(PROJECT_ID, RULE_ID))
                .isInstanceOf(AlertNotFoundException.class);
        verify(repository).findByIdAndProjectId(RULE_ID, PROJECT_ID);
    }

    @Test
    void updatesConfigurationAndEnabledState() {
        AlertRule existing = rule("old", Instant.parse("2026-08-10T09:00:00Z"));
        when(repository.findByIdAndProjectId(RULE_ID, PROJECT_ID)).thenReturn(java.util.Optional.of(existing));
        when(repository.save(existing)).thenAnswer(invocation -> saved(invocation.getArgument(0)));

        AlertRuleResponse updated = service.update(PROJECT_ID, RULE_ID, request("new", "device-2", null, false));
        AlertRuleResponse enabled = service.updateEnabled(PROJECT_ID, RULE_ID, true);

        assertThat(updated.name()).isEqualTo("new");
        assertThat(updated.deviceId()).isEqualTo("device-2");
        assertThat(enabled.enabled()).isTrue();
    }

    @Test
    void deletesOnlyProjectOwnedRule() {
        AlertRule existing = rule("delete", Instant.now());
        when(repository.findByIdAndProjectId(RULE_ID, PROJECT_ID)).thenReturn(java.util.Optional.of(existing));

        service.delete(PROJECT_ID, RULE_ID);

        verify(repository).delete(existing);
    }

    @Test
    void rejectsInvalidRequests() {
        assertThatThrownBy(() -> service.create(PROJECT_ID, request(" ", null, null, true)))
                .isInstanceOf(AlertRuleValidationException.class);
        assertThatThrownBy(() -> service.create(PROJECT_ID, request("x".repeat(201), null, null, true)))
                .isInstanceOf(AlertRuleValidationException.class);
        assertThatThrownBy(() -> service.create(PROJECT_ID, new AlertRuleRequest("x", "x".repeat(2001), AlertRuleMetricType.CPU_USAGE, BigDecimal.ONE, AlertRuleComparisonOperator.EQUAL, Severity.LOW, true, null, null)))
                .isInstanceOf(AlertRuleValidationException.class);
        assertThatThrownBy(() -> service.create(PROJECT_ID, new AlertRuleRequest("x", null, null, BigDecimal.ONE, AlertRuleComparisonOperator.EQUAL, Severity.LOW, true, null, null)))
                .isInstanceOf(AlertRuleValidationException.class);
        assertThatThrownBy(() -> service.create(PROJECT_ID, new AlertRuleRequest("x", null, AlertRuleMetricType.CPU_USAGE, null, AlertRuleComparisonOperator.EQUAL, Severity.LOW, true, null, null)))
                .isInstanceOf(AlertRuleValidationException.class);
        assertThatThrownBy(() -> service.create(PROJECT_ID, new AlertRuleRequest("x", null, AlertRuleMetricType.CPU_USAGE, BigDecimal.ONE, null, Severity.LOW, true, null, null)))
                .isInstanceOf(AlertRuleValidationException.class);
        assertThatThrownBy(() -> service.create(PROJECT_ID, new AlertRuleRequest("x", null, AlertRuleMetricType.CPU_USAGE, BigDecimal.ONE, AlertRuleComparisonOperator.EQUAL, null, true, null, null)))
                .isInstanceOf(AlertRuleValidationException.class);
        assertThatThrownBy(() -> service.create(PROJECT_ID, request("x", "device-1", UUID.randomUUID(), true)))
                .isInstanceOf(AlertRuleValidationException.class);
    }

    private AlertRuleRequest request(String name, String deviceId, UUID serviceId, boolean enabled) {
        return new AlertRuleRequest(name, null, AlertRuleMetricType.CPU_USAGE, BigDecimal.valueOf(80),
                AlertRuleComparisonOperator.GREATER_THAN, Severity.HIGH, enabled, deviceId, serviceId);
    }

    private AlertRule rule(String name, Instant updatedAt) {
        AlertRule rule = new AlertRule();
        rule.setProjectId(PROJECT_ID);
        rule.setName(name);
        rule.setMetricType(AlertRuleMetricType.CPU_USAGE);
        rule.setThresholdValue(BigDecimal.ONE);
        rule.setComparisonOperator(AlertRuleComparisonOperator.EQUAL);
        rule.setSeverity(Severity.LOW);
        rule.setEnabled(true);
        rule.setCreatedAt(updatedAt.minusSeconds(60));
        rule.setUpdatedAt(updatedAt);
        return rule;
    }

    private AlertRule saved(AlertRule rule) {
        if (rule.getCreatedAt() == null) {
            rule.setCreatedAt(Instant.now());
        }
        rule.setUpdatedAt(Instant.now());
        return rule;
    }
}