package com.edgecloud.alert.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.edgecloud.alert.evaluation.AlertEvaluationResponse;
import com.edgecloud.alert.evaluation.AlertEvaluationOrchestrationService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest
@AutoConfigureMockMvc
class AlertRuleEvaluationControllerSecurityTest {

    private static final String SECRET = "edgecloud-monitor-development-secret-key-for-jwt-token-generation";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlertEvaluationOrchestrationService evaluationService;

    @Test
    void missingJwtReturns401() throws Exception {
        mockMvc.perform(post("/internal/alert-rule-evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInput()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestReturnsDtoOnlyResponse() throws Exception {
        when(evaluationService.evaluate(any())).thenReturn(
                new AlertEvaluationResponse(Instant.parse("2026-08-10T00:00:00Z"), false, 1, 1, 1, List.of()));

        mockMvc.perform(post("/internal/alert-rule-evaluations")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInput()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.candidateCount").value(1))
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.alertType").doesNotExist());
    }

    @Test
    void malformedInputReturns400() throws Exception {
        mockMvc.perform(post("/internal/alert-rule-evaluations")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":null}"))
                .andExpect(status().isBadRequest());
    }

    private String validInput() {
        return """
                {"projectId":"%s","sourceType":"DEVICE","sourceId":"device-1","metricType":"CPU_USAGE","observedValue":80,"observedAt":"2026-08-10T00:00:00Z","sampleId":"sample-1"}
                """.formatted(UUID.randomUUID());
    }

    private String bearer() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("evaluation-test")
                .claim("userId", UUID.randomUUID().toString())
                .claim("role", "ADMIN")
                .issuedAt(java.util.Date.from(Instant.now()))
                .expiration(java.util.Date.from(Instant.now().plusSeconds(300)))
                .signWith(key)
                .compact();
        return "Bearer " + token;
    }
}
