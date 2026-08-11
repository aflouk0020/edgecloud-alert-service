package com.edgecloud.alert.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import com.edgecloud.alert.dto.AlertEventResponse;
import com.edgecloud.alert.dto.AlertEventOwnershipHistoryResponse;
import com.edgecloud.alert.entity.AlertEvent;
import com.edgecloud.alert.entity.AlertEventOwnershipAction;
import com.edgecloud.alert.entity.AlertEventOwnershipHistory;
import com.edgecloud.alert.entity.AlertEventStatus;
import com.edgecloud.alert.entity.NotificationLifecycleEventType;
import com.edgecloud.alert.exception.AlertNotFoundException;
import com.edgecloud.alert.exception.AlertOwnershipConflictException;
import com.edgecloud.alert.exception.AlertOwnershipReleaseForbiddenException;
import com.edgecloud.alert.exception.AlertEventValidationException;
import com.edgecloud.alert.exception.InvalidAlertLifecycleTransitionException;
import com.edgecloud.alert.repository.AlertEventOwnershipHistoryRepository;
import com.edgecloud.alert.repository.AlertEventRepository;

@Service
public class AlertOwnershipServiceImpl implements AlertOwnershipService {

    private final AlertEventRepository eventRepository;
    private final AlertEventOwnershipHistoryRepository historyRepository;
    private final Clock clock;
    private final AlertNotificationOutboxService outboxService;

    @Autowired
    public AlertOwnershipServiceImpl(AlertEventRepository eventRepository,
                                     AlertEventOwnershipHistoryRepository historyRepository,
                                     AlertNotificationOutboxService outboxService) {
        this(eventRepository, historyRepository, outboxService, Clock.systemUTC());
    }

    AlertOwnershipServiceImpl(AlertEventRepository eventRepository,
                              AlertEventOwnershipHistoryRepository historyRepository,
                              AlertNotificationOutboxService outboxService,
                              Clock clock) {
        this.eventRepository = eventRepository;
        this.historyRepository = historyRepository;
        this.outboxService = outboxService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AlertEventResponse acknowledge(UUID projectId, UUID alertId, UUID actorUserId, String ownerLabel) {
        validateIdentity(projectId, alertId, actorUserId);
        AlertEvent event = lockedEvent(projectId, alertId);
        if (event.getStatus() == AlertEventStatus.RESOLVED) {
            throw new InvalidAlertLifecycleTransitionException("Resolved alert cannot be acknowledged");
        }
        if (event.getStatus() == AlertEventStatus.ACKNOWLEDGED) {
            if (actorUserId.equals(event.getOwnerUserId())) return AlertEventResponse.from(event);
            throw new AlertOwnershipConflictException("Alert is already acknowledged by another owner");
        }

        String normalizedLabel = normalizeLabel(ownerLabel);
        Instant changedAt = clock.instant();
        event.acknowledge(actorUserId, normalizedLabel, changedAt);
        AlertEvent saved = eventRepository.save(event);
        historyRepository.save(new AlertEventOwnershipHistory(
                saved.getId(), projectId, actorUserId, actorUserId, normalizedLabel,
                AlertEventOwnershipAction.ACKNOWLEDGED, changedAt));
        outboxService.enqueue(saved, NotificationLifecycleEventType.ACKNOWLEDGED, changedAt);
        return AlertEventResponse.from(saved);
    }

    @Override
    @Transactional
    public AlertEventResponse release(UUID projectId, UUID alertId, UUID actorUserId) {
        validateIdentity(projectId, alertId, actorUserId);
        AlertEvent event = lockedEvent(projectId, alertId);
        if (event.getStatus() == AlertEventStatus.RESOLVED) {
            throw new InvalidAlertLifecycleTransitionException("Resolved alert cannot be released");
        }
        if (event.getStatus() != AlertEventStatus.ACKNOWLEDGED) {
            throw new InvalidAlertLifecycleTransitionException("Only an acknowledged alert can be released");
        }
        if (!actorUserId.equals(event.getOwnerUserId())) {
            throw new AlertOwnershipReleaseForbiddenException("Only the current owner can release the alert");
        }

        UUID releasedOwnerId = event.getOwnerUserId();
        String releasedOwnerLabel = event.getOwnerDisplayName();
        Instant changedAt = clock.instant();
        event.release(changedAt);
        AlertEvent saved = eventRepository.save(event);
        historyRepository.save(new AlertEventOwnershipHistory(
                saved.getId(), projectId, actorUserId, releasedOwnerId, releasedOwnerLabel,
                AlertEventOwnershipAction.RELEASED, changedAt));
        return AlertEventResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertEventOwnershipHistoryResponse> history(UUID projectId, UUID alertId) {
        if (projectId == null || alertId == null) {
            throw new AlertNotFoundException("Alert event not found");
        }
        if (eventRepository.findByIdAndProjectId(alertId, projectId).isEmpty()) {
            throw new AlertNotFoundException("Alert event not found");
        }
        return historyRepository.findByProjectIdAndAlertEventIdOrderByChangedAtAscIdAsc(projectId, alertId)
                .stream().map(AlertEventOwnershipHistoryResponse::from).toList();
    }

    private AlertEvent lockedEvent(UUID projectId, UUID alertId) {
        return eventRepository.findByIdAndProjectIdForUpdate(alertId, projectId)
                .orElseThrow(() -> new AlertNotFoundException("Alert event not found"));
    }

    private void validateIdentity(UUID projectId, UUID alertId, UUID actorUserId) {
        if (projectId == null || alertId == null || actorUserId == null) {
            throw new AlertEventValidationException("projectId, alertId and actorUserId are required");
        }
    }

    private String normalizeLabel(String ownerLabel) {
        if (ownerLabel == null || ownerLabel.isBlank()) return null;
        String normalized = ownerLabel.trim();
        if (normalized.length() > 200) {
            throw new AlertEventValidationException("ownerLabel exceeds 200 characters");
        }
        return normalized;
    }
}
