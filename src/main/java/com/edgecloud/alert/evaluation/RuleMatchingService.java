package com.edgecloud.alert.evaluation;

import com.edgecloud.alert.entity.AlertRule;

public interface RuleMatchingService {

    boolean matches(AlertRule rule, AlertEvaluationInput input);

    boolean isValid(AlertRule rule);
}