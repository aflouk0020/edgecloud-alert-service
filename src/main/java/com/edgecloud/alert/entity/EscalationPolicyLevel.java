package com.edgecloud.alert.entity;

import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;

@Entity @Table(name="escalation_policy_levels", uniqueConstraints=@UniqueConstraint(name="uk_escalation_policy_level", columnNames={"policy_id","level_number"}))
public class EscalationPolicyLevel {
    @Id @GeneratedValue(strategy=GenerationType.UUID) @JdbcTypeCode(SqlTypes.CHAR) @Column(length=36) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="policy_id") private EscalationPolicy policy;
    @Column(name="level_number", nullable=false) private int levelNumber;
    @Column(name="elapsed_seconds", nullable=false) private long elapsedSeconds;
    @Enumerated(EnumType.STRING) @Column(name="target_severity", nullable=false, length=16) private Severity targetSeverity;
    @Column(nullable=false) private boolean enabled;
    protected EscalationPolicyLevel() {}
    public EscalationPolicyLevel(int number,long seconds,Severity severity,boolean enabled){levelNumber=number;elapsedSeconds=seconds;targetSeverity=severity;this.enabled=enabled;}
    void attach(EscalationPolicy policy){this.policy=policy;}
    public UUID getId(){return id;} public int getLevelNumber(){return levelNumber;} public long getElapsedSeconds(){return elapsedSeconds;} public Severity getTargetSeverity(){return targetSeverity;} public boolean isEnabled(){return enabled;}
}
