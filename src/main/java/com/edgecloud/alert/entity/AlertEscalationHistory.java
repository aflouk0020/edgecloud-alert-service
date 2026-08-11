package com.edgecloud.alert.entity;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;

@Entity @Table(name="alert_escalation_history", uniqueConstraints=@UniqueConstraint(name="uk_alert_escalation_level", columnNames={"alert_event_id","level_number"}), indexes=@Index(name="idx_escalation_history_project_alert",columnList="project_id,alert_event_id,escalated_at"))
public class AlertEscalationHistory {
    @Id @GeneratedValue(strategy=GenerationType.UUID) @JdbcTypeCode(SqlTypes.CHAR) @Column(length=36) private UUID id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="alert_event_id",nullable=false,length=36) private UUID alertEventId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="project_id",nullable=false,length=36) private UUID projectId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="policy_id",nullable=false,length=36) private UUID policyId;
    @Column(name="level_number",nullable=false) private int levelNumber;
    @Enumerated(EnumType.STRING) @Column(name="previous_severity",nullable=false,length=16) private Severity previousSeverity;
    @Enumerated(EnumType.STRING) @Column(name="resulting_severity",nullable=false,length=16) private Severity resultingSeverity;
    @Column(nullable=false,length=32) private String reason;
    @Column(name="escalated_at",nullable=false) private Instant escalatedAt;
    protected AlertEscalationHistory(){}
    public AlertEscalationHistory(AlertEvent e,UUID policyId,int level,Severity before,Severity after,String reason,Instant at){alertEventId=e.getId();projectId=e.getProjectId();this.policyId=policyId;levelNumber=level;previousSeverity=before;resultingSeverity=after;this.reason=reason;escalatedAt=at;}
    public UUID getId(){return id;} public UUID getAlertEventId(){return alertEventId;} public UUID getProjectId(){return projectId;} public UUID getPolicyId(){return policyId;} public int getLevelNumber(){return levelNumber;} public Severity getPreviousSeverity(){return previousSeverity;} public Severity getResultingSeverity(){return resultingSeverity;} public String getReason(){return reason;} public Instant getEscalatedAt(){return escalatedAt;}
}
