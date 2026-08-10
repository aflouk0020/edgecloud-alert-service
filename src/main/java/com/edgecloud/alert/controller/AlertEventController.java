package com.edgecloud.alert.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edgecloud.alert.dto.AlertEventFilter;
import com.edgecloud.alert.dto.AlertEventPageResponse;
import com.edgecloud.alert.dto.AlertEventResponse;
import com.edgecloud.alert.entity.AlertEventSourceType;
import com.edgecloud.alert.entity.AlertEventStatus;
import com.edgecloud.alert.entity.Severity;
import com.edgecloud.alert.service.AlertEventQueryService;
import com.edgecloud.alert.service.AlertRuleAuthorizationService;

@RestController
@RequestMapping("/api/v2/projects/{projectId}/alerts")
public class AlertEventController {

    private final AlertEventQueryService queryService;
    private final AlertRuleAuthorizationService authorizationService;

    public AlertEventController(AlertEventQueryService queryService,
                                AlertRuleAuthorizationService authorizationService) {
        this.queryService = queryService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ResponseEntity<AlertEventPageResponse> list(
            @PathVariable UUID projectId,
            @RequestParam(required = false) AlertEventStatus status,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) AlertEventSourceType sourceType,
            @RequestParam(required = false) String sourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortDirection,
            Authentication authentication) {
        authorizationService.requireRead(projectId, authentication);
        return ResponseEntity.ok(queryService.list(
                projectId, new AlertEventFilter(status, severity, sourceType, sourceId, from, to),
                page, size, sortDirection));
    }

    @GetMapping("/{alertId}")
    public ResponseEntity<AlertEventResponse> get(
            @PathVariable UUID projectId,
            @PathVariable UUID alertId,
            Authentication authentication) {
        authorizationService.requireRead(projectId, authentication);
        return ResponseEntity.ok(queryService.get(projectId, alertId));
    }
}
