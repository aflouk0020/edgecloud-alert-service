package com.edgecloud.alert.repository;

import com.edgecloud.alert.entity.Alert;
import com.edgecloud.alert.entity.AlertType;
import com.edgecloud.alert.entity.Severity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    List<Alert> findByStatusOrderByCreatedAtDesc(String status);

    long countByStatus(String status);

    long countBySeverity(Severity severity);

    Optional<Alert> findFirstByAlertTypeAndSourceServiceAndStatus(
            AlertType alertType,
            String sourceService,
            String status
    );
}
