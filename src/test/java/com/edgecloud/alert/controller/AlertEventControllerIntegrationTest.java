package com.edgecloud.alert.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.edgecloud.alert.client.ProjectAccessClient;
import com.edgecloud.alert.client.ProjectWorkspaceResponse;
import com.edgecloud.alert.dto.AlertEventFilter;
import com.edgecloud.alert.dto.AlertEventPageResponse;
import com.edgecloud.alert.dto.AlertEventResponse;
import com.edgecloud.alert.dto.AlertEventOwnershipHistoryResponse;
import com.edgecloud.alert.entity.AlertEventOwnershipAction;
import com.edgecloud.alert.entity.AlertEventSourceType;
import com.edgecloud.alert.entity.AlertEventStatus;
import com.edgecloud.alert.entity.AlertRuleComparisonOperator;
import com.edgecloud.alert.entity.AlertRuleMetricType;
import com.edgecloud.alert.entity.Severity;
import com.edgecloud.alert.exception.AlertNotFoundException;
import com.edgecloud.alert.exception.ProjectAccessDeniedException;
import com.edgecloud.alert.service.AlertEventQueryService;
import com.edgecloud.alert.service.AlertOwnershipService;
import com.edgecloud.alert.exception.AlertOwnershipConflictException;
import com.edgecloud.alert.exception.AlertOwnershipReleaseForbiddenException;
import com.edgecloud.alert.exception.InvalidAlertLifecycleTransitionException;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertEventControllerIntegrationTest {

    private static final String SECRET = "edgecloud-monitor-development-secret-key-for-jwt-token-generation";
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID ALERT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired MockMvc mockMvc;
    @MockitoBean ProjectAccessClient projectAccessClient;
    @MockitoBean AlertEventQueryService queryService;
    @MockitoBean AlertOwnershipService ownershipService;

    @BeforeEach
    void setUp() {
        when(projectAccessClient.getWorkspace(eq(PROJECT_ID), any(String.class)))
                .thenReturn(workspace("PROJECT_ADMIN"));
        when(queryService.list(eq(PROJECT_ID), any(), any(), any(), any()))
                .thenReturn(new AlertEventPageResponse(List.of(response()), 0, 20, 1, 1));
        when(queryService.get(PROJECT_ID, ALERT_ID)).thenReturn(response());
        when(ownershipService.acknowledge(eq(PROJECT_ID), eq(ALERT_ID), any(UUID.class), eq(null)))
                .thenAnswer(invocation -> acknowledgedResponse(invocation.getArgument(2)));
        when(ownershipService.release(eq(PROJECT_ID), eq(ALERT_ID), any(UUID.class))).thenReturn(response());
        when(ownershipService.history(PROJECT_ID, ALERT_ID)).thenReturn(List.of(historyResponse()));
    }

    @Test
    void allReadRolesCanListAndRetrieveDetail() throws Exception {
        for (String role : List.of("ADMIN", "PROJECT_ADMIN", "OPERATOR", "VIEWER")) {
            when(projectAccessClient.getWorkspace(eq(PROJECT_ID), any(String.class))).thenReturn(workspace(role));
            mockMvc.perform(get(path("")).header("Authorization", bearer(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.alerts[0].alertId").value(ALERT_ID.toString()))
                    .andExpect(jsonPath("$.alerts[0].status").value("OPEN"))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.totalElements").value(1));
            mockMvc.perform(get(path("/" + ALERT_ID)).header("Authorization", bearer(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()))
                    .andExpect(jsonPath("$.alertRuleName").value("CPU rule"));
        }
    }

    @Test
    void missingAndInvalidJwtReturn401() throws Exception {
        mockMvc.perform(get(path(""))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path("")).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(path("/" + ALERT_ID + "/acknowledgement")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(path("/" + ALERT_ID + "/acknowledgement"))
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void operationalRolesAcknowledgeUsingAuthenticatedJwtUserOnly() throws Exception {
        for (String role : List.of("ADMIN", "PROJECT_ADMIN", "OPERATOR")) {
            when(projectAccessClient.getWorkspace(eq(PROJECT_ID), any(String.class))).thenReturn(workspace(role));
            UUID userId = UUID.randomUUID();
            mockMvc.perform(post(path("/" + ALERT_ID + "/acknowledgement"))
                            .header("Authorization", bearer(role, userId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                    .andExpect(jsonPath("$.ownerUserId").value(userId.toString()));
            verify(ownershipService).acknowledge(PROJECT_ID, ALERT_ID, userId, null);
        }
    }

    @Test
    void viewerAcknowledgementIsDeniedBeforeMutation() throws Exception {
        when(projectAccessClient.getWorkspace(eq(PROJECT_ID), any(String.class))).thenReturn(workspace("VIEWER"));
        mockMvc.perform(post(path("/" + ALERT_ID + "/acknowledgement"))
                        .header("Authorization", bearer("VIEWER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(path("/" + ALERT_ID + "/acknowledgement"))
                        .header("Authorization", bearer("VIEWER")))
                .andExpect(status().isForbidden());
        verify(ownershipService, never()).acknowledge(any(), any(), any(), any());
        verify(ownershipService, never()).release(any(), any(), any());
    }

    @Test
    void detailResponseIncludesCurrentOwnershipFields() throws Exception {
        when(queryService.get(PROJECT_ID, ALERT_ID)).thenReturn(acknowledgedResponse(USER_ID));
        mockMvc.perform(get(path("/" + ALERT_ID)).header("Authorization", bearer("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerUserId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.acknowledgedAt").value("2026-08-10T10:05:00Z"))
                .andExpect(jsonPath("$.ownershipChangedAt").value("2026-08-10T10:05:00Z"));
    }

    @Test
    void acknowledgementIsIdempotentForOwnerAndMapsConflictsTo409() throws Exception {
        mockMvc.perform(post(path("/" + ALERT_ID + "/acknowledgement"))
                        .header("Authorization", bearer("OPERATOR")))
                .andExpect(status().isOk());

        UUID otherAlert = UUID.randomUUID();
        when(ownershipService.acknowledge(PROJECT_ID, otherAlert, USER_ID, null))
                .thenThrow(new AlertOwnershipConflictException("Alert is already acknowledged by another owner"));
        mockMvc.perform(post(path("/" + otherAlert + "/acknowledgement"))
                        .header("Authorization", bearer("OPERATOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Alert is already acknowledged by another owner"));
    }

    @Test
    void resolvedAcknowledgementAndOpenReleaseReturn409() throws Exception {
        UUID resolvedAlert = UUID.randomUUID();
        UUID openAlert = UUID.randomUUID();
        when(ownershipService.acknowledge(PROJECT_ID, resolvedAlert, USER_ID, null))
                .thenThrow(new InvalidAlertLifecycleTransitionException("Resolved alert cannot be acknowledged"));
        when(ownershipService.release(PROJECT_ID, openAlert, USER_ID))
                .thenThrow(new InvalidAlertLifecycleTransitionException("Only an acknowledged alert can be released"));

        mockMvc.perform(post(path("/" + resolvedAlert + "/acknowledgement"))
                        .header("Authorization", bearer("OPERATOR"))).andExpect(status().isConflict());
        mockMvc.perform(delete(path("/" + openAlert + "/acknowledgement"))
                        .header("Authorization", bearer("OPERATOR"))).andExpect(status().isConflict());
    }

    @Test
    void ownerCanReleaseAndNonOwnerReceivesSafe403() throws Exception {
        mockMvc.perform(delete(path("/" + ALERT_ID + "/acknowledgement"))
                        .header("Authorization", bearer("OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        verify(ownershipService).release(PROJECT_ID, ALERT_ID, USER_ID);

        UUID otherAlert = UUID.randomUUID();
        when(ownershipService.release(PROJECT_ID, otherAlert, USER_ID))
                .thenThrow(new AlertOwnershipReleaseForbiddenException("Only the current owner can release"));
        mockMvc.perform(delete(path("/" + otherAlert + "/acknowledgement"))
                        .header("Authorization", bearer("OPERATOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void allReadRolesCanReadProjectScopedOwnershipHistory() throws Exception {
        for (String role : List.of("ADMIN", "PROJECT_ADMIN", "OPERATOR", "VIEWER")) {
            when(projectAccessClient.getWorkspace(eq(PROJECT_ID), any(String.class))).thenReturn(workspace(role));
            mockMvc.perform(get(path("/" + ALERT_ID + "/ownership-history"))
                            .header("Authorization", bearer(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].alertId").value(ALERT_ID.toString()))
                    .andExpect(jsonPath("$[0].action").value("ACKNOWLEDGED"));
        }
    }

    @Test
    void inaccessibleProjectIsRejectedBeforeOwnershipCalls() throws Exception {
        when(projectAccessClient.getWorkspace(eq(PROJECT_ID), any(String.class)))
                .thenThrow(new ProjectAccessDeniedException("Access denied"));
        mockMvc.perform(get(path("/" + ALERT_ID + "/ownership-history"))
                        .header("Authorization", bearer("VIEWER"))).andExpect(status().isForbidden());
        mockMvc.perform(post(path("/" + ALERT_ID + "/acknowledgement"))
                        .header("Authorization", bearer("OPERATOR"))).andExpect(status().isForbidden());
        verify(ownershipService, never()).history(any(), any());
        verify(ownershipService, never()).acknowledge(any(), any(), any(), any());
    }

    @Test
    void missingOrCrossProjectAlertReturns404() throws Exception {
        UUID foreignAlert = UUID.randomUUID();
        when(ownershipService.acknowledge(PROJECT_ID, foreignAlert, USER_ID, null))
                .thenThrow(new AlertNotFoundException("Alert event not found"));
        when(ownershipService.history(PROJECT_ID, foreignAlert))
                .thenThrow(new AlertNotFoundException("Alert event not found"));
        mockMvc.perform(post(path("/" + foreignAlert + "/acknowledgement"))
                        .header("Authorization", bearer("OPERATOR"))).andExpect(status().isNotFound());
        mockMvc.perform(get(path("/" + foreignAlert + "/ownership-history"))
                        .header("Authorization", bearer("VIEWER"))).andExpect(status().isNotFound());
    }

    @Test
    void forwardsOwnerFilterAndRejectsMalformedOwnerId() throws Exception {
        UUID ownerId = UUID.randomUUID();
        mockMvc.perform(get(path("?ownerId=" + ownerId)).header("Authorization", bearer("VIEWER")))
                .andExpect(status().isOk());
        ArgumentCaptor<AlertEventFilter> filter = ArgumentCaptor.forClass(AlertEventFilter.class);
        verify(queryService).list(eq(PROJECT_ID), filter.capture(), any(), any(), any());
        assertThat(filter.getValue().ownerId()).isEqualTo(ownerId);

        mockMvc.perform(get(path("?ownerId=not-a-uuid")).header("Authorization", bearer("VIEWER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedIdentifiersAndEnumsReturn400() throws Exception {
        mockMvc.perform(get("/api/v2/projects/not-a-uuid/alerts").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(path("/not-a-uuid")).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(path("?status=OWNED")).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inaccessibleAndArchivedProjectsReturn403BeforeQuery() throws Exception {
        when(projectAccessClient.getWorkspace(eq(PROJECT_ID), any(String.class)))
                .thenThrow(new ProjectAccessDeniedException("Access denied"));

        mockMvc.perform(get(path("")).header("Authorization", bearer("PROJECT_ADMIN")))
                .andExpect(status().isForbidden());
        verify(queryService, never()).list(any(), any(), any(), any(), any());

        clearInvocations(queryService);
        when(projectAccessClient.getWorkspace(eq(PROJECT_ID), any(String.class)))
                .thenThrow(new ProjectAccessDeniedException("Archived project"));
        mockMvc.perform(get(path("")).header("Authorization", bearer("PROJECT_ADMIN")))
                .andExpect(status().isForbidden());
        verify(queryService, never()).list(any(), any(), any(), any(), any());
    }

    @Test
    void forwardsFiltersAndPagination() throws Exception {
        mockMvc.perform(get(path("?status=RESOLVED&severity=HIGH&sourceType=DEVICE&sourceId=device-1"
                        + "&from=2026-08-10T09:00:00Z&to=2026-08-10T10:00:00Z&page=2&size=50&sortDirection=ASC"))
                        .header("Authorization", bearer("VIEWER")))
                .andExpect(status().isOk());

        ArgumentCaptor<AlertEventFilter> filter = ArgumentCaptor.forClass(AlertEventFilter.class);
        verify(queryService).list(eq(PROJECT_ID), filter.capture(), eq(2), eq(50), eq("ASC"));
        assertThat(filter.getValue()).isEqualTo(new AlertEventFilter(
                AlertEventStatus.RESOLVED, Severity.HIGH, AlertEventSourceType.DEVICE, "device-1",
                null,
                Instant.parse("2026-08-10T09:00:00Z"), Instant.parse("2026-08-10T10:00:00Z")));
    }

    @Test
    void queryValidationReturns400AndCrossProjectDetailReturns404() throws Exception {
        when(queryService.list(eq(PROJECT_ID), any(), eq(-1), any(), any()))
                .thenThrow(new com.edgecloud.alert.exception.AlertEventValidationException("page must be zero or greater"));
        mockMvc.perform(get(path("?page=-1")).header("Authorization", bearer("VIEWER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("page must be zero or greater"));

        UUID foreignAlert = UUID.randomUUID();
        when(queryService.get(PROJECT_ID, foreignAlert)).thenThrow(new AlertNotFoundException("Alert event not found"));
        mockMvc.perform(get(path("/" + foreignAlert)).header("Authorization", bearer("VIEWER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Alert event not found"));
    }

    private AlertEventResponse response() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        return new AlertEventResponse(
                ALERT_ID, UUID.randomUUID(), "CPU rule", PROJECT_ID, AlertEventSourceType.DEVICE, "device-1",
                AlertRuleMetricType.CPU_USAGE, new BigDecimal("95.25"), new BigDecimal("80.00"),
                AlertRuleComparisonOperator.GREATER_THAN, Severity.HIGH, AlertEventStatus.OPEN,
                now, now, null, null, null, null, null, now, now);
    }

    private AlertEventResponse acknowledgedResponse(UUID ownerId) {
        AlertEventResponse base = response();
        Instant now = Instant.parse("2026-08-10T10:05:00Z");
        return new AlertEventResponse(
                base.alertId(), base.alertRuleId(), base.alertRuleName(), base.projectId(), base.sourceType(),
                base.sourceId(), base.metricType(), base.observedValue(), base.thresholdValue(),
                base.comparisonOperator(), base.severity(), AlertEventStatus.ACKNOWLEDGED,
                base.triggeredAt(), base.lastObservedAt(), null, ownerId, null, now, now,
                base.createdAt(), now);
    }

    private AlertEventOwnershipHistoryResponse historyResponse() {
        return new AlertEventOwnershipHistoryResponse(
                UUID.randomUUID(), ALERT_ID, PROJECT_ID, AlertEventOwnershipAction.ACKNOWLEDGED,
                USER_ID, USER_ID, null, Instant.parse("2026-08-10T10:05:00Z"));
    }

    private ProjectWorkspaceResponse workspace(String role) {
        return new ProjectWorkspaceResponse(PROJECT_ID, "Project", "ACTIVE", UUID.randomUUID(), role,
                List.of(), List.of());
    }

    private String path(String suffix) {
        return "/api/v2/projects/" + PROJECT_ID + "/alerts" + suffix;
    }

    private String bearer(String role) {
        return bearer(role, USER_ID);
    }

    private String bearer(String role, UUID userId) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
                .subject("test@example.com")
                .claim("userId", userId.toString())
                .claim("role", role)
                .issuedAt(java.util.Date.from(Instant.now()))
                .expiration(java.util.Date.from(Instant.now().plusSeconds(300)))
                .signWith(key)
                .compact();
    }
}
