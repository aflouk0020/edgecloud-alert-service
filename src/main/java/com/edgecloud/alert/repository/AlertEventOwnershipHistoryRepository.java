package com.edgecloud.alert.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.edgecloud.alert.entity.AlertEventOwnershipHistory;

public interface AlertEventOwnershipHistoryRepository extends JpaRepository<AlertEventOwnershipHistory, UUID> {

    List<AlertEventOwnershipHistory> findByProjectIdAndAlertEventIdOrderByChangedAtAscIdAsc(
            UUID projectId, UUID alertEventId);
}
