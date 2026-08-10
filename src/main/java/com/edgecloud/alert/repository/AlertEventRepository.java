package com.edgecloud.alert.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.edgecloud.alert.entity.AlertEvent;
import com.edgecloud.alert.entity.AlertEventSourceType;
import com.edgecloud.alert.entity.AlertEventStatus;
import com.edgecloud.alert.entity.AlertRuleMetricType;

import jakarta.persistence.LockModeType;

public interface AlertEventRepository extends JpaRepository<AlertEvent, UUID>, JpaSpecificationExecutor<AlertEvent> {

    Optional<AlertEvent> findByIdAndProjectId(UUID id, UUID projectId);

    Optional<AlertEvent> findByProjectIdAndAlertRuleIdAndSourceTypeAndSourceIdAndMetricTypeAndStatus(
            UUID projectId, UUID alertRuleId, AlertEventSourceType sourceType, String sourceId,
            AlertRuleMetricType metricType, AlertEventStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event from AlertEvent event
            where event.projectId = :projectId
              and event.alertRuleId = :alertRuleId
              and event.sourceType = :sourceType
              and event.sourceId = :sourceId
              and event.metricType = :metricType
              and event.status = com.edgecloud.alert.entity.AlertEventStatus.OPEN
            """)
    Optional<AlertEvent> findOpenForUpdate(
            @Param("projectId") UUID projectId,
            @Param("alertRuleId") UUID alertRuleId,
            @Param("sourceType") AlertEventSourceType sourceType,
            @Param("sourceId") String sourceId,
            @Param("metricType") AlertRuleMetricType metricType);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO alert_events (
                id, alert_rule_id, alert_rule_name, project_id, source_type, source_id,
                metric_type, observed_value, threshold_value, comparison_operator,
                severity, status, triggered_at, last_observed_at, resolved_at, created_at, updated_at
            ) VALUES (
                :id, :alertRuleId, :alertRuleName, :projectId, :sourceType, :sourceId,
                :metricType, :observedValue, :thresholdValue, :comparisonOperator,
                :severity, 'OPEN', :evaluatedAt, :evaluatedAt, NULL, :evaluatedAt, :evaluatedAt
            )
            ON DUPLICATE KEY UPDATE
                alert_rule_name = VALUES(alert_rule_name),
                observed_value = VALUES(observed_value),
                threshold_value = VALUES(threshold_value),
                comparison_operator = VALUES(comparison_operator),
                severity = VALUES(severity),
                last_observed_at = VALUES(last_observed_at),
                updated_at = VALUES(updated_at)
            """, nativeQuery = true)
    int upsertOpen(
            @Param("id") String id,
            @Param("alertRuleId") String alertRuleId,
            @Param("alertRuleName") String alertRuleName,
            @Param("projectId") String projectId,
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("metricType") String metricType,
            @Param("observedValue") BigDecimal observedValue,
            @Param("thresholdValue") BigDecimal thresholdValue,
            @Param("comparisonOperator") String comparisonOperator,
            @Param("severity") String severity,
            @Param("evaluatedAt") Instant evaluatedAt);
}
