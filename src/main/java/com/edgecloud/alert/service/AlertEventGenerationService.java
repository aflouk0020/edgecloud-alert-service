package com.edgecloud.alert.service;

import java.util.Optional;

import com.edgecloud.alert.dto.AlertEventResponse;
import com.edgecloud.alert.evaluation.AlertEvaluationResult;

public interface AlertEventGenerationService {

    Optional<AlertEventResponse> process(AlertEvaluationResult result);
}
