package com.edgecloud.alert.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.edgecloud.alert.entity.AlertRule;

public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {

    List<AlertRule> findByProjectIdOrderByUpdatedAtDescIdAsc(UUID projectId);

    Optional<AlertRule> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByIdAndProjectId(UUID id, UUID projectId);
}