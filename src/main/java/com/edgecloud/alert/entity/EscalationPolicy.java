package com.edgecloud.alert.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;

@Entity
@Table(name = "escalation_policies", uniqueConstraints = @UniqueConstraint(name="uk_escalation_policy_project", columnNames="project_id"))
public class EscalationPolicy {
    @Id @GeneratedValue(strategy=GenerationType.UUID) @JdbcTypeCode(SqlTypes.CHAR) @Column(length=36) private UUID id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="project_id", nullable=false, length=36) private UUID projectId;
    @Column(nullable=false, length=200) private String name;
    @Column(nullable=false) private boolean enabled;
    @OneToMany(mappedBy="policy", cascade=CascadeType.ALL, orphanRemoval=true, fetch=FetchType.EAGER)
    @OrderBy("levelNumber asc") private List<EscalationPolicyLevel> levels = new ArrayList<>();
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    protected EscalationPolicy() {}
    public EscalationPolicy(UUID projectId, String name, boolean enabled) { this.projectId=projectId; this.name=name; this.enabled=enabled; this.createdAt=Instant.now(); this.updatedAt=createdAt; }
    public void replace(String name, boolean enabled, List<EscalationPolicyLevel> replacement) { this.name=name; this.enabled=enabled; levels.clear(); replacement.forEach(this::addLevel); updatedAt=Instant.now(); }
    public void addLevel(EscalationPolicyLevel level) { level.attach(this); levels.add(level); }
    public void setEnabled(boolean enabled) { this.enabled=enabled; updatedAt=Instant.now(); }
    public UUID getId(){return id;} public UUID getProjectId(){return projectId;} public String getName(){return name;} public boolean isEnabled(){return enabled;} public List<EscalationPolicyLevel> getLevels(){return List.copyOf(levels);} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
