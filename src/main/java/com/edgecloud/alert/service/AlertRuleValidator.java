package com.edgecloud.alert.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.edgecloud.alert.dto.AlertRuleRequest;
import com.edgecloud.alert.exception.AlertRuleValidationException;

@Component
public class AlertRuleValidator {

    static final int MAX_NAME_LENGTH = 200;
    static final int MAX_DESCRIPTION_LENGTH = 2000;

    public void validate(UUID projectId, AlertRuleRequest request) {
        if (projectId == null) {
            throw new AlertRuleValidationException("projectId is required");
        }
        if (request == null) {
            throw new AlertRuleValidationException("Rule request is required");
        }
        String name = request.name() == null ? null : request.name().trim();
        if (name == null || name.isEmpty()) {
            throw new AlertRuleValidationException("name is required");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new AlertRuleValidationException("name must not exceed 200 characters");
        }
        if (request.description() != null && request.description().length() > MAX_DESCRIPTION_LENGTH) {
            throw new AlertRuleValidationException("description must not exceed 2000 characters");
        }
        if (request.metricType() == null) {
            throw new AlertRuleValidationException("metricType is required");
        }
        BigDecimal threshold = request.thresholdValue();
        if (threshold == null) {
            throw new AlertRuleValidationException("thresholdValue is required and must be finite");
        }
        if (request.comparisonOperator() == null) {
            throw new AlertRuleValidationException("comparisonOperator is required");
        }
        if (request.severity() == null) {
            throw new AlertRuleValidationException("severity is required");
        }
        if (request.deviceId() != null && request.serviceId() != null) {
            throw new AlertRuleValidationException("deviceId and serviceId cannot both be set");
        }
    }
}