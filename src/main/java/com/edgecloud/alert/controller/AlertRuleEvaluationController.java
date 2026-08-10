package com.edgecloud.alert.controller;

import com.edgecloud.alert.evaluation.AlertEvaluationInput;
import com.edgecloud.alert.evaluation.AlertEvaluationResponse;
import com.edgecloud.alert.evaluation.AlertRuleEvaluationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/alert-rule-evaluations")
public class AlertRuleEvaluationController {

    private final AlertRuleEvaluationService evaluationService;

    public AlertRuleEvaluationController(AlertRuleEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping
    public ResponseEntity<AlertEvaluationResponse> evaluate(@Valid @RequestBody AlertEvaluationInput input) {
        return ResponseEntity.ok(evaluationService.evaluate(input));
    }
}