package com.edgecloud.alert.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false)
    private String sourceService;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private boolean resolved;

    private LocalDateTime resolvedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        status = "ACTIVE";
        resolved = false;
        createdAt = LocalDateTime.now();
    }

    public void resolve() {
        status = "RESOLVED";
        resolved = true;
        resolvedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public AlertType getAlertType() {
        return alertType;
    }

    public void setAlertType(AlertType alertType) {
        this.alertType = alertType;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSourceService() {
        return sourceService;
    }

    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
    }

    public String getStatus() {
        return status;
    }

    public boolean isResolved() {
        return resolved;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
