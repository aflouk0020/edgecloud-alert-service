package com.edgecloud.alert.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AlertNotificationOutboxTest {

    @Test
    void snapshotsLifecycleDataKeepsStableSourceIdAndTracksRetryThenPublish() {
        AlertEvent event = mock(AlertEvent.class);
        UUID alertId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-08-11T10:00:00Z");
        when(event.getId()).thenReturn(alertId);
        when(event.getProjectId()).thenReturn(projectId);
        when(event.getSeverity()).thenReturn(Severity.HIGH);
        when(event.getAlertRuleName()).thenReturn("CPU overload");
        when(event.getMetricType()).thenReturn(AlertRuleMetricType.CPU_USAGE);
        when(event.getSourceType()).thenReturn(AlertEventSourceType.DEVICE);
        when(event.getSourceId()).thenReturn("device-1");
        when(event.getObservedValue()).thenReturn(new BigDecimal("95.25"));
        when(event.getThresholdValue()).thenReturn(new BigDecimal("80.00"));

        AlertNotificationOutbox item = new AlertNotificationOutbox(
                event, NotificationLifecycleEventType.OPENED, occurredAt);
        UUID stableSourceId = item.getSourceEventId();
        item.claim(occurredAt.plusSeconds(1));
        item.retry(occurredAt.plusSeconds(2), occurredAt.plusSeconds(32), "HTTP_503", "unavailable");
        item.claim(occurredAt.plusSeconds(32));
        item.publish(occurredAt.plusSeconds(33));

        assertThat(item.getId()).isEqualTo(stableSourceId);
        assertThat(item.getAlertEventId()).isEqualTo(alertId);
        assertThat(item.getProjectId()).isEqualTo(projectId);
        assertThat(item.getRuleName()).isEqualTo("CPU overload");
        assertThat(item.getObservedValue()).isEqualByComparingTo("95.25");
        assertThat(item.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(item.getAttemptCount()).isEqualTo(2);
        assertThat(item.getStatus()).isEqualTo(AlertNotificationOutboxStatus.PUBLISHED);
        assertThat(item.getSourceEventId()).isEqualTo(stableSourceId);
    }
}
