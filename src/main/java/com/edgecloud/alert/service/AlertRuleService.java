package com.edgecloud.alert.service;

import java.util.List;
import java.util.UUID;

import com.edgecloud.alert.dto.AlertRuleRequest;
import com.edgecloud.alert.dto.AlertRuleResponse;

public interface AlertRuleService {

    AlertRuleResponse create(UUID projectId, AlertRuleRequest request);

    List<AlertRuleResponse> listByProject(UUID projectId);

    AlertRuleResponse get(UUID projectId, UUID ruleId);

    AlertRuleResponse update(UUID projectId, UUID ruleId, AlertRuleRequest request);

    AlertRuleResponse updateEnabled(UUID projectId, UUID ruleId, boolean enabled);

    void delete(UUID projectId, UUID ruleId);
}