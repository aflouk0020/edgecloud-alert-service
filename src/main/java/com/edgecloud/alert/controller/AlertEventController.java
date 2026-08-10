package com.edgecloud.alert.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.edgecloud.alert.service.AlertOwnershipService;
import com.edgecloud.alert.dto.AlertEventOwnershipHistoryResponse;
import com.edgecloud.alert.security.EdgeCloudJwtAuthenticationToken;
import com.edgecloud.alert.exception.ProjectAccessDeniedException;
import java.util.List;

@RestController
@RequestMapping("/api/v2/projects/{projectId}/alerts")
public class AlertEventController {

    private final AlertEventQueryService queryService;
    private final AlertRuleAuthorizationService authorizationService;
    private final AlertOwnershipService ownershipService;

    public AlertEventController(AlertEventQueryService queryService,
                                AlertRuleAuthorizationService authorizationService,
                                AlertOwnershipService ownershipService) {
        this.queryService = queryService;
        this.authorizationService = authorizationService;
        this.ownershipService = ownershipService;
    }

    @GetMapping
    public ResponseEntity<AlertEventPageResponse> list(
            @PathVariable UUID projectId,
            @RequestParam(required = false) AlertEventStatus status,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) AlertEventSourceType sourceType,
            @RequestParam(required = false) String sourceId,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortDirection,
            Authentication authentication) {
        authorizationService.requireRead(projectId, authentication);
        return ResponseEntity.ok(queryService.list(
                projectId, new AlertEventFilter(status, severity, sourceType, sourceId, ownerId, from, to),
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

    @PostMapping("/{alertId}/acknowledgement")
    public ResponseEntity<AlertEventResponse> acknowledge(
            @PathVariable UUID projectId,
            @PathVariable UUID alertId,
            Authentication authentication) {
        authorizationService.requireMutation(projectId, authentication);
        return ResponseEntity.ok(ownershipService.acknowledge(
                projectId, alertId, authenticatedUserId(authentication), null));
    }

    @DeleteMapping("/{alertId}/acknowledgement")
    public ResponseEntity<AlertEventResponse> release(
            @PathVariable UUID projectId,
            @PathVariable UUID alertId,
            Authentication authentication) {
        authorizationService.requireMutation(projectId, authentication);
        return ResponseEntity.ok(ownershipService.release(
                projectId, alertId, authenticatedUserId(authentication)));
    }

    @GetMapping("/{alertId}/ownership-history")
    public ResponseEntity<List<AlertEventOwnershipHistoryResponse>> ownershipHistory(
            @PathVariable UUID projectId,
            @PathVariable UUID alertId,
            Authentication authentication) {
        authorizationService.requireRead(projectId, authentication);
        return ResponseEntity.ok(ownershipService.history(projectId, alertId));
    }

    private UUID authenticatedUserId(Authentication authentication) {
        if (authentication instanceof EdgeCloudJwtAuthenticationToken token) return token.getUserId();
        throw new ProjectAccessDeniedException("Access denied");
    }
}
