package com.edgecloud.alert.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "alert_rules", indexes = {
        @Index(name = "idx_alert_rules_project", columnList = "project_id"),
        @Index(name = "idx_alert_rules_project_enabled", columnList = "project_id, enabled"),
        @Index(name = "idx_alert_rules_project_updated", columnList = "project_id, updated_at")
})
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 36)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "project_id", nullable = false, length = 36)
    private UUID projectId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 32)
    private AlertRuleMetricType metricType;

    @Column(name = "threshold_value", nullable = false, precision = 20, scale = 6)
    private BigDecimal thresholdValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_operator", nullable = false, length = 32)
    private AlertRuleComparisonOperator comparisonOperator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Severity severity;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "service_id", length = 36)
    private UUID serviceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AlertRule() {
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public AlertRuleMetricType getMetricType() { return metricType; }
    public BigDecimal getThresholdValue() { return thresholdValue; }
    public AlertRuleComparisonOperator getComparisonOperator() { return comparisonOperator; }
    public Severity getSeverity() { return severity; }
    public boolean isEnabled() { return enabled; }
    public String getDeviceId() { return deviceId; }
    public UUID getServiceId() { return serviceId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setMetricType(AlertRuleMetricType metricType) { this.metricType = metricType; }
    public void setThresholdValue(BigDecimal thresholdValue) { this.thresholdValue = thresholdValue; }
    public void setComparisonOperator(AlertRuleComparisonOperator comparisonOperator) { this.comparisonOperator = comparisonOperator; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public void setServiceId(UUID serviceId) { this.serviceId = serviceId; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}