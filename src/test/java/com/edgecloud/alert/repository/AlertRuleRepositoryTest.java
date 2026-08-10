package com.edgecloud.alert.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.edgecloud.alert.entity.AlertRule;
import com.edgecloud.alert.entity.AlertRuleComparisonOperator;
import com.edgecloud.alert.entity.AlertRuleMetricType;
import com.edgecloud.alert.entity.Severity;

@DataJpaTest
class AlertRuleRepositoryTest {

    @Autowired
    private AlertRuleRepository repository;

    @Test
    void persistsTargetVariantsAndOrdersByUpdatedAtThenId() {
        UUID projectId = UUID.randomUUID();
        AlertRule projectWide = rule(projectId, "project-wide", true, null, null);
        AlertRule deviceRule = rule(projectId, "device", true, "device-1", null);
        AlertRule serviceRule = rule(projectId, "service", true, null, UUID.randomUUID());

        repository.saveAndFlush(projectWide);
        repository.saveAndFlush(deviceRule);
        repository.saveAndFlush(serviceRule);

        assertThat(repository.findByProjectIdOrderByUpdatedAtDescIdAsc(projectId))
                .extracting(AlertRule::getName)
                .containsExactly("service", "device", "project-wide");
        assertThat(repository.findByIdAndProjectId(deviceRule.getId(), projectId)).contains(deviceRule);
        assertThat(repository.findByIdAndProjectId(deviceRule.getId(), UUID.randomUUID())).isEmpty();
    }

    private AlertRule rule(UUID projectId, String name, boolean enabled, String deviceId, UUID serviceId) {
        AlertRule rule = new AlertRule();
        rule.setProjectId(projectId);
        rule.setName(name);
        rule.setMetricType(AlertRuleMetricType.CPU_USAGE);
        rule.setThresholdValue(BigDecimal.ONE);
        rule.setComparisonOperator(AlertRuleComparisonOperator.GREATER_THAN);
        rule.setSeverity(Severity.LOW);
        rule.setEnabled(enabled);
        rule.setDeviceId(deviceId);
        rule.setServiceId(serviceId);
        rule.setCreatedAt(java.time.Instant.now());
        rule.setUpdatedAt(java.time.Instant.now().plusSeconds(name.equals("service") ? 3 : name.equals("device") ? 2 : 1));
        return rule;
    }
}