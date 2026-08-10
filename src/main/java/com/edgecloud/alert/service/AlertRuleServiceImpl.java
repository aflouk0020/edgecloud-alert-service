package com.edgecloud.alert.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edgecloud.alert.dto.AlertRuleRequest;
import com.edgecloud.alert.dto.AlertRuleResponse;
import com.edgecloud.alert.entity.AlertRule;
import com.edgecloud.alert.exception.AlertNotFoundException;
import com.edgecloud.alert.exception.AlertRuleValidationException;
import com.edgecloud.alert.repository.AlertRuleRepository;

@Service
@Transactional
public class AlertRuleServiceImpl implements AlertRuleService {

    private final AlertRuleRepository repository;
    private final AlertRuleValidator validator;

    public AlertRuleServiceImpl(AlertRuleRepository repository, AlertRuleValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    @Override
    public AlertRuleResponse create(UUID projectId, AlertRuleRequest request) {
        validator.validate(projectId, request);
        AlertRule rule = new AlertRule();
        rule.setProjectId(projectId);
        apply(rule, request);
        return toResponse(repository.save(rule));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertRuleResponse> listByProject(UUID projectId) {
        if (projectId == null) {
            throw new AlertRuleValidationException("projectId is required");
        }
        return repository.findByProjectIdOrderByUpdatedAtDescIdAsc(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AlertRuleResponse get(UUID projectId, UUID ruleId) {
        return toResponse(find(projectId, ruleId));
    }

    @Override
    public AlertRuleResponse update(UUID projectId, UUID ruleId, AlertRuleRequest request) {
        validator.validate(projectId, request);
        AlertRule rule = find(projectId, ruleId);
        apply(rule, request);
        return toResponse(repository.save(rule));
    }

    @Override
    public AlertRuleResponse updateEnabled(UUID projectId, UUID ruleId, boolean enabled) {
        AlertRule rule = find(projectId, ruleId);
        rule.setEnabled(enabled);
        return toResponse(repository.save(rule));
    }

    @Override
    public void delete(UUID projectId, UUID ruleId) {
        repository.delete(find(projectId, ruleId));
    }

    private AlertRule find(UUID projectId, UUID ruleId) {
        if (projectId == null || ruleId == null) {
            throw new AlertNotFoundException("Alert rule not found");
        }
        return repository.findByIdAndProjectId(ruleId, projectId)
                .orElseThrow(() -> new AlertNotFoundException("Alert rule not found: " + ruleId));
    }

    private void apply(AlertRule rule, AlertRuleRequest request) {
        rule.setName(request.name().trim());
        rule.setDescription(request.description());
        rule.setMetricType(request.metricType());
        rule.setThresholdValue(request.thresholdValue());
        rule.setComparisonOperator(request.comparisonOperator());
        rule.setSeverity(request.severity());
        rule.setEnabled(request.enabled());
        rule.setDeviceId(request.deviceId());
        rule.setServiceId(request.serviceId());
    }

    private AlertRuleResponse toResponse(AlertRule rule) {
        return new AlertRuleResponse(
                rule.getId(), rule.getProjectId(), rule.getName(), rule.getDescription(),
                rule.getMetricType(), rule.getThresholdValue(), rule.getComparisonOperator(),
                rule.getSeverity(), rule.isEnabled(), rule.getDeviceId(), rule.getServiceId(),
                rule.getCreatedAt(), rule.getUpdatedAt());
    }
}