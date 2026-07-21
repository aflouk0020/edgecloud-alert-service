package com.edgecloud.alert.repository;

import com.edgecloud.alert.entity.Notification;
import com.edgecloud.alert.entity.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByStatusOrderByCreatedAtDesc(NotificationStatus status);

    Optional<Notification> findFirstByAlertId(UUID alertId);

    long countByStatus(NotificationStatus status);

}
