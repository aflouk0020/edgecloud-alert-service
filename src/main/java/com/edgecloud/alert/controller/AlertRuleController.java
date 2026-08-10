package com.edgecloud.alert.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edgecloud.alert.dto.AlertRuleEnabledRequest;
import com.edgecloud.alert.dto.AlertRuleRequest;
import com.edgecloud.alert.dto.AlertRuleResponse;
import com.edgecloud.alert.service.AlertRuleAuthorizationService;
import com.edgecloud.alert.service.AlertRuleService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v2/projects/{projectId}/alert-rules")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;
    private final AlertRuleAuthorizationService authorizationService;

    public AlertRuleController(AlertRuleService alertRuleService,
                               AlertRuleAuthorizationService authorizationService) {
        this.alertRuleService = alertRuleService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ResponseEntity<List<AlertRuleResponse>> list(@PathVariable UUID projectId,
                                                        Authentication authentication) {
        authorizationService.requireRead(projectId, authentication);
        return ResponseEntity.ok(alertRuleService.listByProject(projectId));
    }

    @PostMapping
    public ResponseEntity<AlertRuleResponse> create(@PathVariable UUID projectId,
                                                    @Valid @RequestBody AlertRuleRequest request,
                                                    Authentication authentication) {
        authorizationService.requireMutation(projectId, authentication, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(alertRuleService.create(projectId, request));
    }

    @GetMapping("/{ruleId}")
    public ResponseEntity<AlertRuleResponse> get(@PathVariable UUID projectId,
                                                 @PathVariable UUID ruleId,
                                                 Authentication authentication) {
        authorizationService.requireRead(projectId, authentication);
        return ResponseEntity.ok(alertRuleService.get(projectId, ruleId));
    }

    @PutMapping("/{ruleId}")
    public ResponseEntity<AlertRuleResponse> update(@PathVariable UUID projectId,
                                                    @PathVariable UUID ruleId,
                                                    @Valid @RequestBody AlertRuleRequest request,
                                                    Authentication authentication) {
        authorizationService.requireMutation(projectId, authentication, request);
        return ResponseEntity.ok(alertRuleService.update(projectId, ruleId, request));
    }

    @PatchMapping("/{ruleId}/enabled")
    public ResponseEntity<AlertRuleResponse> updateEnabled(@PathVariable UUID projectId,
                                                           @PathVariable UUID ruleId,
                                                           @Valid @RequestBody AlertRuleEnabledRequest request,
                                                           Authentication authentication) {
        authorizationService.requireMutation(projectId, authentication);
        return ResponseEntity.ok(alertRuleService.updateEnabled(projectId, ruleId, request.enabled()));
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId,
                                       @PathVariable UUID ruleId,
                                       Authentication authentication) {
        authorizationService.requireMutation(projectId, authentication);
        alertRuleService.delete(projectId, ruleId);
        return ResponseEntity.noContent().build();
    }
}