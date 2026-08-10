package com.edgecloud.alert.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.edgecloud.alert.entity.AlertEventSourceType;
import com.edgecloud.alert.entity.AlertEventStatus;
import com.edgecloud.alert.entity.AlertRuleComparisonOperator;
import com.edgecloud.alert.entity.AlertRuleMetricType;
import com.edgecloud.alert.entity.Severity;
import com.edgecloud.alert.exception.AlertNotFoundException;
import com.edgecloud.alert.exception.ProjectAccessDeniedException;
import com.edgecloud.alert.service.AlertEventQueryService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertEventControllerIntegrationTest {

    private static final String SECRET = "edgecloud-monitor-development-secret-key-for-jwt-token-generation";
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID ALERT_ID = UUID.randomUUID();

    @Autowired MockMvc mockMvc;
    @MockitoBean ProjectAccessClient projectAccessClient;
    @MockitoBean AlertEventQueryService queryService;

    @BeforeEach
    void setUp() {
        when(projectAccessClient.getWorkspace(eq(PROJECT_ID), any(String.class)))
                .thenReturn(workspace("PROJECT_ADMIN"));
        when(queryService.list(eq(PROJECT_ID), any(), any(), any(), any()))
                .thenReturn(new AlertEventPageResponse(List.of(response()), 0, 20, 1, 1));
        when(queryService.get(PROJECT_ID, ALERT_ID)).thenReturn(response());
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
    }

    @Test
    void malformedIdentifiersAndEnumsReturn400() throws Exception {
        mockMvc.perform(get("/api/v2/projects/not-a-uuid/alerts").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(path("/not-a-uuid")).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(path("?status=ACKNOWLEDGED")).header("Authorization", bearer("ADMIN")))
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
                now, now, null, now, now);
    }

    private ProjectWorkspaceResponse workspace(String role) {
        return new ProjectWorkspaceResponse(PROJECT_ID, "Project", "ACTIVE", UUID.randomUUID(), role,
                List.of(), List.of());
    }

    private String path(String suffix) {
        return "/api/v2/projects/" + PROJECT_ID + "/alerts" + suffix;
    }

    private String bearer(String role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
                .subject("test@example.com")
                .claim("userId", UUID.randomUUID().toString())
                .claim("role", role)
                .issuedAt(java.util.Date.from(Instant.now()))
                .expiration(java.util.Date.from(Instant.now().plusSeconds(300)))
                .signWith(key)
                .compact();
    }
}
