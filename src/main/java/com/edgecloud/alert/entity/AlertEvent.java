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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "alert_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_alert_events_open", columnNames = {
                "project_id", "alert_rule_id", "source_type", "source_id", "metric_type", "open_marker"
        }),
        indexes = {
                @Index(name = "idx_alert_events_project_status", columnList = "project_id,status,triggered_at,id"),
                @Index(name = "idx_alert_events_project_severity", columnList = "project_id,severity,triggered_at,id"),
                @Index(name = "idx_alert_events_project_triggered", columnList = "project_id,triggered_at,id"),
                @Index(name = "idx_alert_events_project_owner", columnList = "project_id,owner_user_id,triggered_at,id"),
                @Index(name = "idx_alert_events_source", columnList = "source_type,source_id"),
                @Index(name = "idx_alert_events_rule_status", columnList = "alert_rule_id,status")
        })
public class AlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 36)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "alert_rule_id", nullable = false, length = 36)
    private UUID alertRuleId;

    @Column(name = "alert_rule_name", nullable = false, length = 200)
    private String alertRuleName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "project_id", nullable = false, length = 36)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private AlertEventSourceType sourceType;

    @Column(name = "source_id", nullable = false, length = 128)
    private String sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 32)
    private AlertRuleMetricType metricType;

    @Column(name = "observed_value", nullable = false, precision = 20, scale = 6)
    private BigDecimal observedValue;

    @Column(name = "threshold_value", nullable = false, precision = 20, scale = 6)
    private BigDecimal thresholdValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_operator", nullable = false, length = 32)
    private AlertRuleComparisonOperator comparisonOperator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertEventStatus status;

    @Column(name = "triggered_at", nullable = false, updatable = false)
    private Instant triggeredAt;

    @Column(name = "last_observed_at", nullable = false)
    private Instant lastObservedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "owner_user_id", length = 36)
    private UUID ownerUserId;

    @Column(name = "owner_display_name", length = 200)
    private String ownerDisplayName;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "ownership_changed_at")
    private Instant ownershipChangedAt;

    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "open_marker", insertable = false, updatable = false)
    private Integer openMarker;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (status == null) status = AlertEventStatus.OPEN;
        if (triggeredAt == null) triggeredAt = now;
        if (lastObservedAt == null) lastObservedAt = triggeredAt;
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void resolve(Instant observedAt) {
        status = AlertEventStatus.RESOLVED;
        lastObservedAt = observedAt;
        resolvedAt = observedAt;
        updatedAt = observedAt;
    }

    public void acknowledge(UUID ownerUserId, String ownerDisplayName, Instant changedAt) {
        status = AlertEventStatus.ACKNOWLEDGED;
        this.ownerUserId = ownerUserId;
        this.ownerDisplayName = ownerDisplayName;
        acknowledgedAt = changedAt;
        ownershipChangedAt = changedAt;
        updatedAt = changedAt;
    }

    public void release(Instant changedAt) {
        status = AlertEventStatus.OPEN;
        ownerUserId = null;
        ownerDisplayName = null;
        acknowledgedAt = null;
        ownershipChangedAt = changedAt;
        updatedAt = changedAt;
    }

    public void escalate(int level, Severity targetSeverity, Instant at) {
        if (targetSeverity.ordinal() > severity.ordinal()) severity = targetSeverity;
        escalationLevel = level;
        escalatedAt = at;
        updatedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getAlertRuleId() { return alertRuleId; }
    public String getAlertRuleName() { return alertRuleName; }
    public UUID getProjectId() { return projectId; }
    public AlertEventSourceType getSourceType() { return sourceType; }
    public String getSourceId() { return sourceId; }
    public AlertRuleMetricType getMetricType() { return metricType; }
    public BigDecimal getObservedValue() { return observedValue; }
    public BigDecimal getThresholdValue() { return thresholdValue; }
    public AlertRuleComparisonOperator getComparisonOperator() { return comparisonOperator; }
    public Severity getSeverity() { return severity; }
    public AlertEventStatus getStatus() { return status; }
    public Instant getTriggeredAt() { return triggeredAt; }
    public Instant getLastObservedAt() { return lastObservedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public String getOwnerDisplayName() { return ownerDisplayName; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Instant getOwnershipChangedAt() { return ownershipChangedAt; }
    public int getEscalationLevel() { return escalationLevel; }
    public Instant getEscalatedAt() { return escalatedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Integer getOpenMarker() { return openMarker; }

    public void setAlertRuleId(UUID alertRuleId) { this.alertRuleId = alertRuleId; }
    public void setAlertRuleName(String alertRuleName) { this.alertRuleName = alertRuleName; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public void setSourceType(AlertEventSourceType sourceType) { this.sourceType = sourceType; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public void setMetricType(AlertRuleMetricType metricType) { this.metricType = metricType; }
    public void setObservedValue(BigDecimal observedValue) { this.observedValue = observedValue; }
    public void setThresholdValue(BigDecimal thresholdValue) { this.thresholdValue = thresholdValue; }
    public void setComparisonOperator(AlertRuleComparisonOperator comparisonOperator) { this.comparisonOperator = comparisonOperator; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public void setStatus(AlertEventStatus status) { this.status = status; }
    public void setTriggeredAt(Instant triggeredAt) { this.triggeredAt = triggeredAt; }
    public void setLastObservedAt(Instant lastObservedAt) { this.lastObservedAt = lastObservedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public void setOwnerUserId(UUID ownerUserId) { this.ownerUserId = ownerUserId; }
    public void setOwnerDisplayName(String ownerDisplayName) { this.ownerDisplayName = ownerDisplayName; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public void setOwnershipChangedAt(Instant ownershipChangedAt) { this.ownershipChangedAt = ownershipChangedAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
