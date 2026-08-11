package com.edgecloud.alert.entity;

import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes; import jakarta.persistence.*;
@Entity @Table(name="alert_suppression_history",uniqueConstraints=@UniqueConstraint(name="uk_suppression_window_dedup",columnNames={"maintenance_window_id","dedup_key"}),indexes=@Index(name="idx_suppression_project_window",columnList="project_id,maintenance_window_id,suppressed_at"))
public class AlertSuppressionHistory {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @JdbcTypeCode(SqlTypes.CHAR) @Column(length=36) private UUID id;
 @JdbcTypeCode(SqlTypes.CHAR) @Column(name="project_id",nullable=false,length=36) private UUID projectId;
 @JdbcTypeCode(SqlTypes.CHAR) @Column(name="maintenance_window_id",nullable=false,length=36) private UUID maintenanceWindowId;
 @JdbcTypeCode(SqlTypes.CHAR) @Column(name="alert_rule_id",nullable=false,length=36) private UUID alertRuleId;
 @Column(name="rule_name",nullable=false,length=200) private String ruleName;
 @Enumerated(EnumType.STRING) @Column(name="source_type",nullable=false,length=16) private AlertEventSourceType sourceType;
 @Column(name="source_id",nullable=false,length=128) private String sourceId;
 @Enumerated(EnumType.STRING) @Column(name="metric_type",nullable=false,length=32) private AlertRuleMetricType metricType;
 @Column(name="observed_value",nullable=false,precision=20,scale=6) private BigDecimal observedValue;
 @Column(name="threshold_value",nullable=false,precision=20,scale=6) private BigDecimal thresholdValue;
 @Column(name="evaluated_at",nullable=false) private Instant evaluatedAt;
 @Column(name="suppressed_at",nullable=false) private Instant suppressedAt;
 @Column(name="window_name",nullable=false,length=200) private String windowName;
 @Column(name="reason",nullable=false,length=1000) private String reason;
 @Column(name="origin",nullable=false,length=32) private String origin;
 @Column(name="dedup_key",nullable=false,length=64) private String dedupKey;
 protected AlertSuppressionHistory(){}
 public AlertSuppressionHistory(UUID projectId,UUID windowId,UUID ruleId,String ruleName,AlertEventSourceType sourceType,String sourceId,AlertRuleMetricType metricType,BigDecimal observed,BigDecimal threshold,Instant evaluatedAt,Instant suppressedAt,String windowName,String reason,String dedupKey){this.projectId=projectId;maintenanceWindowId=windowId;alertRuleId=ruleId;this.ruleName=ruleName;this.sourceType=sourceType;this.sourceId=sourceId;this.metricType=metricType;observedValue=observed;thresholdValue=threshold;this.evaluatedAt=evaluatedAt;this.suppressedAt=suppressedAt;this.windowName=windowName;this.reason=reason;origin="SYSTEM_EVALUATION";this.dedupKey=dedupKey;}
 public UUID getId(){return id;} public UUID getProjectId(){return projectId;} public UUID getMaintenanceWindowId(){return maintenanceWindowId;} public UUID getAlertRuleId(){return alertRuleId;} public String getRuleName(){return ruleName;} public AlertEventSourceType getSourceType(){return sourceType;} public String getSourceId(){return sourceId;} public AlertRuleMetricType getMetricType(){return metricType;} public BigDecimal getObservedValue(){return observedValue;} public BigDecimal getThresholdValue(){return thresholdValue;} public Instant getEvaluatedAt(){return evaluatedAt;} public Instant getSuppressedAt(){return suppressedAt;} public String getWindowName(){return windowName;} public String getReason(){return reason;} public String getOrigin(){return origin;} public String getDedupKey(){return dedupKey;}
}
