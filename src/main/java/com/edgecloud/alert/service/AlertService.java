package com.edgecloud.alert.service;

import com.edgecloud.alert.dto.AlertResponse;
import com.edgecloud.alert.dto.AlertSummaryResponse;
import com.edgecloud.alert.dto.CreateAlertRequest;
import com.edgecloud.alert.dto.RuleEvaluationRequest;

import java.util.List;
import java.util.UUID;

public interface AlertService {

    AlertResponse createAlert(CreateAlertRequest request);

    List<AlertResponse> getActiveAlerts();

    AlertSummaryResponse getAlertSummary();

    AlertResponse resolveAlert(UUID id);

    List<AlertResponse> evaluateRules(RuleEvaluationRequest request);
}
