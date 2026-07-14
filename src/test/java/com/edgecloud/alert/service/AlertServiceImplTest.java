package com.edgecloud.alert.service;

import com.edgecloud.alert.dto.AlertSummaryResponse;
import com.edgecloud.alert.entity.Severity;
import com.edgecloud.alert.repository.AlertRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceImplTest {

    @Mock
    private AlertRepository alertRepository;

    @InjectMocks
    private AlertServiceImpl alertService;

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
}
