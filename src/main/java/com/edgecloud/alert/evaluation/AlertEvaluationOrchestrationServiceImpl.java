package com.edgecloud.alert.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.edgecloud.alert.service.AlertEventGenerationService;

@Service
public class AlertEvaluationOrchestrationServiceImpl implements AlertEvaluationOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluationOrchestrationServiceImpl.class);

    private final AlertRuleEvaluationService evaluationService;
    private final AlertEventGenerationService generationService;

    public AlertEvaluationOrchestrationServiceImpl(AlertRuleEvaluationService evaluationService,
                                                   AlertEventGenerationService generationService) {
        this.evaluationService = evaluationService;
        this.generationService = generationService;
    }

    @Override
    public AlertEvaluationResponse evaluate(AlertEvaluationInput input) {
        AlertEvaluationResponse response = evaluationService.evaluate(input);
        for (AlertEvaluationResult result : response.results()) {
            try {
                generationService.process(result);
            } catch (RuntimeException ex) {
                log.error("Alert lifecycle persistence failed ruleId={} projectId={} sourceType={} sourceId={} metricType={}",
                        result.ruleId(), result.projectId(), result.sourceType(), result.sourceId(), result.metricType(), ex);
            }
        }
        return response;
    }
}
