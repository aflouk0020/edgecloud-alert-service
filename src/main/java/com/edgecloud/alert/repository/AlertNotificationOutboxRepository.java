package com.edgecloud.alert.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.edgecloud.alert.entity.AlertNotificationOutbox;
import com.edgecloud.alert.entity.AlertNotificationOutboxStatus;

import jakarta.persistence.LockModeType;

public interface AlertNotificationOutboxRepository extends JpaRepository<AlertNotificationOutbox, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select item from AlertNotificationOutbox item
            where item.status = com.edgecloud.alert.entity.AlertNotificationOutboxStatus.PENDING
               or (item.status = com.edgecloud.alert.entity.AlertNotificationOutboxStatus.RETRY_SCHEDULED
                   and item.nextAttemptAt <= :now)
            order by item.createdAt, item.id
            """)
    List<AlertNotificationOutbox> lockEligible(@Param("now") Instant now, Pageable pageable);

    List<AlertNotificationOutbox> findByStatusAndProcessingStartedAtBefore(
            AlertNotificationOutboxStatus status, Instant cutoff);

    long countByAlertEventIdAndEventType(UUID alertEventId,
            com.edgecloud.alert.entity.NotificationLifecycleEventType eventType);
}
