package com.edgecloud.alert.entity;

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
import jakarta.persistence.Table;

@Entity
@Table(name = "alert_event_ownership_history", indexes = {
        @Index(name = "idx_alert_ownership_event_changed", columnList = "alert_event_id,changed_at,id"),
        @Index(name = "idx_alert_ownership_project_event_changed", columnList = "project_id,alert_event_id,changed_at,id")
})
public class AlertEventOwnershipHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 36)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "alert_event_id", nullable = false, updatable = false, length = 36)
    private UUID alertEventId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "project_id", nullable = false, updatable = false, length = 36)
    private UUID projectId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "actor_user_id", nullable = false, updatable = false, length = 36)
    private UUID actorUserId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "owner_user_id", updatable = false, length = 36)
    private UUID ownerUserId;

    @Column(name = "owner_display_name", updatable = false, length = 200)
    private String ownerDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 24)
    private AlertEventOwnershipAction action;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected AlertEventOwnershipHistory() {
    }

    public AlertEventOwnershipHistory(UUID alertEventId, UUID projectId, UUID actorUserId,
                                      UUID ownerUserId, String ownerDisplayName,
                                      AlertEventOwnershipAction action, Instant changedAt) {
        this.alertEventId = alertEventId;
        this.projectId = projectId;
        this.actorUserId = actorUserId;
        this.ownerUserId = ownerUserId;
        this.ownerDisplayName = ownerDisplayName;
        this.action = action;
        this.changedAt = changedAt;
    }

    public UUID getId() { return id; }
    public UUID getAlertEventId() { return alertEventId; }
    public UUID getProjectId() { return projectId; }
    public UUID getActorUserId() { return actorUserId; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public String getOwnerDisplayName() { return ownerDisplayName; }
    public AlertEventOwnershipAction getAction() { return action; }
    public Instant getChangedAt() { return changedAt; }
}
