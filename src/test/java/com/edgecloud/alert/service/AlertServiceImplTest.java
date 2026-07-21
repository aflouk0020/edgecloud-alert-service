package com.edgecloud.alert.service;

import com.edgecloud.alert.config.AlertThresholdProperties;
import com.edgecloud.alert.dto.AlertResponse;
import com.edgecloud.alert.dto.AlertSummaryResponse;
import com.edgecloud.alert.dto.RuleEvaluationRequest;
import com.edgecloud.alert.entity.Alert;
import com.edgecloud.alert.entity.AlertType;
import com.edgecloud.alert.entity.Severity;
import com.edgecloud.alert.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceImplTest {

    @Mock
    private AlertRepository alertRepository;

    private AlertServiceImpl alertService;

    @BeforeEach
    void setUp() {
        alertService = new AlertServiceImpl(
                alertRepository,
                new AlertThresholdProperties(1000)
        );
    }

    @Test
    void calculatesAlertLifecycleAndSeveritySummary() {
        when(alertRepository.count()).thenReturn(10L);
        when(alertRepository.countByStatus("ACTIVE")).thenReturn(4L);
        when(alertRepository.countByStatus("RESOLVED")).thenReturn(6L);
        when(alertRepository.countBySeverity(Severity.LOW)).thenReturn(2L);
        when(alertRepository.countBySeverity(Severity.MEDIUM)).thenReturn(3L);
        when(alertRepository.countBySeverity(Severity.HIGH)).thenReturn(5L);

        AlertSummaryResponse response = alertService.getAlertSummary();

        assertThat(response.totalAlerts()).isEqualTo(10);
        assertThat(response.activeAlerts()).isEqualTo(4);
        assertThat(response.resolvedAlerts()).isEqualTo(6);
        assertThat(response.lowSeverityAlerts()).isEqualTo(2);
        assertThat(response.mediumSeverityAlerts()).isEqualTo(3);
        assertThat(response.highSeverityAlerts()).isEqualTo(5);
    }

    @Test
    void createsHighLatencyAlertWhenConfiguredThresholdIsExceeded() {
        when(alertRepository.findFirstByAlertTypeAndSourceServiceAndStatus(
                AlertType.HIGH_LATENCY,
                "monitoring-service",
                "ACTIVE"
        )).thenReturn(Optional.empty());

        when(alertRepository.save(any(Alert.class)))
                .thenAnswer(invocation -> {
                    Alert alert = invocation.getArgument(0);
                    alert.prePersist();
                    return alert;
                });

        RuleEvaluationRequest request = new RuleEvaluationRequest(
                "monitoring-service",
                "UP",
                1500L,
                null,
                null
        );

        List<AlertResponse> responses = alertService.evaluateRules(request);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().alertType())
                .isEqualTo(AlertType.HIGH_LATENCY);
        assertThat(responses.getFirst().severity())
                .isEqualTo(Severity.MEDIUM);

        verify(alertRepository).save(any(Alert.class));
    }

    @Test
    void doesNotCreateLatencyAlertWhenConfiguredThresholdIsNotExceeded() {
        AlertServiceImpl serviceWithHigherThreshold =
                new AlertServiceImpl(
                        alertRepository,
                        new AlertThresholdProperties(2000)
                );

        RuleEvaluationRequest request = new RuleEvaluationRequest(
                "monitoring-service",
                "UP",
                1500L,
                null,
                null
        );

        List<AlertResponse> responses =
                serviceWithHigherThreshold.evaluateRules(request);

        assertThat(responses).isEmpty();
        verifyNoInteractions(alertRepository);
    }
}
