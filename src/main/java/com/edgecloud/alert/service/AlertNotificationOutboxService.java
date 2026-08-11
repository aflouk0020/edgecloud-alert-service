package com.edgecloud.alert.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.edgecloud.alert.entity.AlertEvent;
import com.edgecloud.alert.entity.AlertNotificationOutbox;
import com.edgecloud.alert.entity.NotificationLifecycleEventType;
import com.edgecloud.alert.repository.AlertNotificationOutboxRepository;

@Service
public class AlertNotificationOutboxService {
    private final AlertNotificationOutboxRepository repository;

    public AlertNotificationOutboxService(AlertNotificationOutboxRepository repository) {
        this.repository = repository;
    }

    public AlertNotificationOutbox enqueue(AlertEvent event, NotificationLifecycleEventType type, Instant occurredAt) {
        return repository.save(new AlertNotificationOutbox(event, type, occurredAt));
    }
}
