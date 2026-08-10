package com.edgecloud.alert.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import com.edgecloud.alert.client.ProjectAccessClient;
import com.edgecloud.alert.client.ProjectWorkspaceResponse;
import com.edgecloud.alert.dto.AlertRuleResponse;
import com.edgecloud.alert.exception.ProjectAccessDeniedException;
import com.edgecloud.alert.service.AlertRuleService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertRuleControllerSecurityTest {

    private static final String SECRET = "edgecloud-monitor-development-secret-key-for-jwt-token-generation";
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID RULE_ID = UUID.randomUUID();
    private static final UUID SERVICE_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectAccessClient projectAccessClient;

    @MockitoBean
    private AlertRuleService alertRuleService;

    @BeforeEach
    void setUp() {
        when(projectAccessClient.getWorkspace(eq(PROJECT_ID), any(String.class)))
                .thenReturn(workspace("PROJECT_ADMIN"));
        when(alertRuleService.listByProject(PROJECT_ID)).thenReturn(List.of());
    }

    @Test
    void missingJwtReturns401() throws Exception {
        mockMvc.perform(get(path(""))).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidJwtReturns401() throws Exception {
        mockMvc.perform(get(path("")).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyAlertsRemainAccessibleWithoutJwt() throws Exception {
        mockMvc.perform(get("/alerts")).andExpect(status().isOk());
    }

    @Test
    void adminProjectAdminAndOperatorCanMutateAndViewerCanOnlyRead() throws Exception {
        for (String role : List.of("ADMIN", "PROJECT_ADMIN", "OPERATOR")) {
            when(projectAccessClient.getWorkspace(eq(PROJECT_ID), any(String.class)))
                    .thenReturn(workspace(role));
            mockMvc.perform(post(path(""))
                            .header("Authorization", bearer(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequest()))
                    .andExpect(status().isCreated());
        }

        clearInvocations(alertRuleService);
        when(projectAccessClient.getWorkspace(eq(PROJECT_ID), any(String.class)))
                .thenReturn(workspace("VIEWER"));
        mockMvc.perform(get(path("")).header("Authorization", bearer("VIEWER")))
                .andExpect(status().isOk());
        mockMvc.perform(post(path(""))
                        .header("Authorization", bearer("VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());
        verify(alertRuleService, never()).create(eq(PROJECT_ID), any());
    }

    @Test
    void inaccessibleProjectReturns403BeforeMutation() throws Exception {
        when(projectAccessClient.getWorkspace(eq(PROJECT_ID), any(String.class)))
                .thenThrow(new ProjectAccessDeniedException("Access denied"));

        mockMvc.perform(post(path(""))
                        .header("Authorization", bearer("PROJECT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());
        verify(alertRuleService, never()).create(any(), any());
    }

        @Test
        void archivedProjectIsDeniedBeforeMutation() throws Exception {
                when(projectAccessClient.getWorkspace(eq(PROJECT_ID), any(String.class)))
                                .thenThrow(new ProjectAccessDeniedException("Archived project"));

                mockMvc.perform(get(path("")).header("Authorization", bearer("PROJECT_ADMIN")))
                                .andExpect(status().isForbidden());
                verify(alertRuleService, never()).listByProject(any());
        }

    @Test
    void unassociatedDeviceAndServiceReturn400BeforeMutation() throws Exception {
        when(projectAccessClient.getWorkspace(eq(PROJECT_ID), any(String.class)))
                .thenReturn(workspace("PROJECT_ADMIN"));

        mockMvc.perform(post(path(""))
                        .header("Authorization", bearer("PROJECT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("\"deviceId\":null", "\"deviceId\":\"missing\"")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(path(""))
                        .header("Authorization", bearer("PROJECT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("\"serviceId\":null", "\"serviceId\":\"%s\"".formatted(UUID.randomUUID()))))
                .andExpect(status().isBadRequest());

        verify(alertRuleService, never()).create(any(), any());
    }

    @Test
    void supportsCreateListGetUpdateEnableDisableAndDelete() throws Exception {
        when(alertRuleService.create(eq(PROJECT_ID), any())).thenReturn(response());
        when(alertRuleService.get(PROJECT_ID, RULE_ID)).thenReturn(response());
        when(alertRuleService.update(eq(PROJECT_ID), eq(RULE_ID), any())).thenReturn(response());
        when(alertRuleService.updateEnabled(PROJECT_ID, RULE_ID, true)).thenReturn(response());

        mockMvc.perform(post(path(""))
                        .header("Authorization", bearer("PROJECT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated());
        mockMvc.perform(get(path("")).header("Authorization", bearer("PROJECT_ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get(path("/" + RULE_ID)).header("Authorization", bearer("PROJECT_ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(put(path("/" + RULE_ID))
                        .header("Authorization", bearer("PROJECT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk());
        mockMvc.perform(patch(path("/" + RULE_ID + "/enabled"))
                        .header("Authorization", bearer("PROJECT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete(path("/" + RULE_ID))
                        .header("Authorization", bearer("PROJECT_ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void projectWideAndAssociatedTargetsAreAccepted() throws Exception {
        when(projectAccessClient.getWorkspace(eq(PROJECT_ID), any(String.class)))
                .thenReturn(workspace("PROJECT_ADMIN"));
        when(alertRuleService.create(eq(PROJECT_ID), any())).thenReturn(response());

        mockMvc.perform(post(path(""))
                        .header("Authorization", bearer("PROJECT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated());
        mockMvc.perform(post(path(""))
                        .header("Authorization", bearer("PROJECT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("\"deviceId\":null", "\"deviceId\":\"device-1\"")))
                .andExpect(status().isCreated());
        mockMvc.perform(post(path(""))
                        .header("Authorization", bearer("PROJECT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("\"serviceId\":null", "\"serviceId\":\"%s\"".formatted(SERVICE_ID))))
                .andExpect(status().isCreated());
    }

    private ProjectWorkspaceResponse workspace(String role) {
        return new ProjectWorkspaceResponse(PROJECT_ID, "Project", "ACTIVE", UUID.randomUUID(), role,
                List.of(SERVICE_ID), List.of("device-1"));
    }

    private AlertRuleResponse response() {
        return new AlertRuleResponse(RULE_ID, PROJECT_ID, "CPU", null, null, null, null, null,
                true, null, null, Instant.now(), Instant.now());
    }

    private String path(String suffix) {
        return "/api/v2/projects/" + PROJECT_ID + "/alert-rules" + suffix;
    }

    private String validRequest() {
        return """
                {"name":"CPU","description":null,"metricType":"CPU_USAGE","thresholdValue":80,"comparisonOperator":"GREATER_THAN","severity":"HIGH","enabled":true,"deviceId":null,"serviceId":null}
                """;
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
