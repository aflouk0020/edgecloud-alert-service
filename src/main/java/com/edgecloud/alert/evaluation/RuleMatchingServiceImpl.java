package com.edgecloud.alert.evaluation;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.edgecloud.alert.entity.AlertRule;

@Service
public class RuleMatchingServiceImpl implements RuleMatchingService {

    @Override
    public boolean matches(AlertRule rule, AlertEvaluationInput input) {
        if (!isValid(rule) || !rule.getProjectId().equals(input.projectId())
                || rule.getMetricType() != input.metricType()) {
            return false;
        }
        if (rule.getDeviceId() == null && rule.getServiceId() == null) {
            return true;
        }
        return switch (input.sourceType()) {
            case DEVICE -> rule.getDeviceId() != null && rule.getDeviceId().equals(input.sourceId());
            case SERVICE -> matchesService(rule, input.sourceId());
        };
    }

    @Override
    public boolean isValid(AlertRule rule) {
        if (rule == null || rule.getId() == null || rule.getProjectId() == null
                || rule.getMetricType() == null || rule.getThresholdValue() == null
                || rule.getComparisonOperator() == null || rule.getSeverity() == null) {
            return false;
        }
        return !(rule.getDeviceId() != null && rule.getServiceId() != null);
    }

    private boolean matchesService(AlertRule rule, String sourceId) {
        try {
            return rule.getServiceId() != null && rule.getServiceId().equals(UUID.fromString(sourceId));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}