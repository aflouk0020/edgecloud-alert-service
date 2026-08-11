package com.edgecloud.alert.entity;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;

@Entity
@Table(name = "maintenance_windows", indexes = {
        @Index(name = "idx_maintenance_active_project", columnList = "project_id,enabled,starts_at,ends_at"),
        @Index(name = "idx_maintenance_service", columnList = "project_id,service_id,starts_at,ends_at"),
        @Index(name = "idx_maintenance_device", columnList = "project_id,device_id,starts_at,ends_at")
})
public class MaintenanceWindow {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 36) private UUID id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "project_id", nullable = false, length = 36) private UUID projectId;
    @Enumerated(EnumType.STRING) @Column(name = "scope_type", nullable = false, length = 16) private MaintenanceScopeType scopeType;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "service_id", length = 36) private UUID serviceId;
    @Column(name = "device_id", length = 128) private String deviceId;
    @Column(nullable = false, length = 200) private String name;
    @Column(nullable = false, length = 1000) private String reason;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @Column(nullable = false) private boolean enabled;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "created_by_user_id", nullable = false, length = 36) private UUID createdByUserId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected MaintenanceWindow() {}
    public MaintenanceWindow(UUID projectId, MaintenanceScopeType scopeType, UUID serviceId, String deviceId,
            String name, String reason, Instant startsAt, Instant endsAt, boolean enabled, UUID actor) {
        this.projectId=projectId; this.createdByUserId=actor; this.createdAt=Instant.now();
        update(scopeType,serviceId,deviceId,name,reason,startsAt,endsAt,enabled);
    }
    public void update(MaintenanceScopeType scopeType, UUID serviceId, String deviceId, String name,
            String reason, Instant startsAt, Instant endsAt, boolean enabled) {
        this.scopeType=scopeType; this.serviceId=serviceId; this.deviceId=deviceId;
        this.name=name.trim(); this.reason=reason.trim(); this.startsAt=startsAt; this.endsAt=endsAt;
        this.enabled=enabled; this.updatedAt=Instant.now();
    }
    public void disable(){enabled=false;updatedAt=Instant.now();}
    public boolean activeAt(Instant at){return enabled && !startsAt.isAfter(at) && endsAt.isAfter(at);}
    public boolean matches(AlertEventSourceType type,String source){if(scopeType==MaintenanceScopeType.PROJECT)return true;if(scopeType==MaintenanceScopeType.SERVICE)return type==AlertEventSourceType.SERVICE&&serviceId.toString().equals(source);return type==AlertEventSourceType.DEVICE&&deviceId.equals(source);}
    public UUID getId(){return id;} public UUID getProjectId(){return projectId;} public MaintenanceScopeType getScopeType(){return scopeType;} public UUID getServiceId(){return serviceId;} public String getDeviceId(){return deviceId;} public String getName(){return name;} public String getReason(){return reason;} public Instant getStartsAt(){return startsAt;} public Instant getEndsAt(){return endsAt;} public boolean isEnabled(){return enabled;} public UUID getCreatedByUserId(){return createdByUserId;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
