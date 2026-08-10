package com.edgecloud.alert.evaluation;

public interface AlertRuleEvaluationService {

    AlertEvaluationResponse evaluate(AlertEvaluationInput input);
}