package com.edgecloud.alert.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.edgecloud.alert.entity.AlertRule;
import com.edgecloud.alert.entity.AlertRuleMetricType;

public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {

    List<AlertRule> findByProjectIdOrderByUpdatedAtDescIdAsc(UUID projectId);

    Optional<AlertRule> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByIdAndProjectId(UUID id, UUID projectId);

        @Query("""
            select rule from AlertRule rule
            where rule.projectId = :projectId
              and rule.enabled = true
              and rule.metricType = :metricType
            order by rule.updatedAt desc, rule.id asc
            """)
        List<AlertRule> findEnabledByProjectIdAndMetricType(
            @Param("projectId") UUID projectId,
            @Param("metricType") AlertRuleMetricType metricType);
}