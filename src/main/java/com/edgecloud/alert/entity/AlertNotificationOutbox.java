package com.edgecloud.alert.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "alert_notification_outbox", uniqueConstraints = {
        @UniqueConstraint(name = "uk_alert_notification_source", columnNames = "source_event_id"),
        @UniqueConstraint(name = "uk_alert_notification_transition",
                columnNames = {"alert_event_id", "event_type", "occurred_at"})
})
public class AlertNotificationOutbox {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 36)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "source_event_id", nullable = false, updatable = false, length = 36)
    private UUID sourceEventId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "alert_event_id", nullable = false, updatable = false, length = 36)
    private UUID alertEventId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "project_id", nullable = false, updatable = false, length = 36)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 32)
    private NotificationLifecycleEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private Severity severity;

    @Column(name = "rule_name", nullable = false, updatable = false, length = 200)
    private String ruleName;

    @Column(name = "metric_type", nullable = false, updatable = false, length = 64)
    private String metricType;

    @Column(name = "source_type", nullable = false, updatable = false, length = 64)
    private String sourceType;

    @Column(name = "source_id", nullable = false, updatable = false, length = 128)
    private String sourceId;

    @Column(name = "observed_value", updatable = false, precision = 30, scale = 10)
    private BigDecimal observedValue;

    @Column(name = "threshold_value", updatable = false, precision = 30, scale = 10)
    private BigDecimal thresholdValue;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AlertNotificationOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "next_attempt_at") private Instant nextAttemptAt;
    @Column(name = "last_attempt_at") private Instant lastAttemptAt;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "processing_started_at") private Instant processingStartedAt;
    @Column(name = "failure_category", length = 64) private String failureCategory;
    @Column(name = "failure_message", length = 1000) private String failureMessage;
    @Column(name = "escalation_level") private Integer escalationLevel;
    @Column(name = "escalation_reason", length = 64) private String escalationReason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected AlertNotificationOutbox() { }

    public AlertNotificationOutbox(AlertEvent event, NotificationLifecycleEventType eventType, Instant occurredAt) {
        UUID sourceId = UUID.randomUUID();
        this.id = sourceId;
        this.sourceEventId = sourceId;
        this.alertEventId = event.getId();
        this.projectId = event.getProjectId();
        this.eventType = eventType;
        this.severity = event.getSeverity();
        this.ruleName = event.getAlertRuleName();
        this.metricType = event.getMetricType().name();
        this.sourceType = event.getSourceType().name();
        this.sourceId = event.getSourceId();
        this.observedValue = event.getObservedValue();
        this.thresholdValue = event.getThresholdValue();
        this.occurredAt = occurredAt;
        this.status = AlertNotificationOutboxStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public AlertNotificationOutbox(AlertEvent event, int level, String reason, Instant occurredAt) {
        this(event, NotificationLifecycleEventType.ESCALATED, occurredAt);
        escalationLevel = level;
        escalationReason = reason;
    }

    public void claim(Instant now) {
        status = AlertNotificationOutboxStatus.PROCESSING;
        processingStartedAt = now;
        lastAttemptAt = now;
        attemptCount++;
        nextAttemptAt = null;
        failureCategory = null;
        failureMessage = null;
        updatedAt = now;
    }

    public void publish(Instant now) {
        status = AlertNotificationOutboxStatus.PUBLISHED;
        publishedAt = now;
        processingStartedAt = null;
        updatedAt = now;
    }

    public void retry(Instant now, Instant next, String category, String message) {
        status = AlertNotificationOutboxStatus.RETRY_SCHEDULED;
        nextAttemptAt = next;
        processingStartedAt = null;
        failureCategory = category;
        failureMessage = truncate(message);
        updatedAt = now;
    }

    public void fail(Instant now, String category, String message) {
        status = AlertNotificationOutboxStatus.FAILED;
        nextAttemptAt = null;
        processingStartedAt = null;
        failureCategory = category;
        failureMessage = truncate(message);
        updatedAt = now;
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    public UUID getId() { return id; }
    public UUID getSourceEventId() { return sourceEventId; }
    public UUID getAlertEventId() { return alertEventId; }
    public UUID getProjectId() { return projectId; }
    public NotificationLifecycleEventType getEventType() { return eventType; }
    public Severity getSeverity() { return severity; }
    public String getRuleName() { return ruleName; }
    public String getMetricType() { return metricType; }
    public String getSourceType() { return sourceType; }
    public String getSourceId() { return sourceId; }
    public BigDecimal getObservedValue() { return observedValue; }
    public BigDecimal getThresholdValue() { return thresholdValue; }
    public Instant getOccurredAt() { return occurredAt; }
    public AlertNotificationOutboxStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getProcessingStartedAt() { return processingStartedAt; }
    public String getFailureCategory() { return failureCategory; }
    public String getFailureMessage() { return failureMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Integer getEscalationLevel() { return escalationLevel; }
    public String getEscalationReason() { return escalationReason; }
}
