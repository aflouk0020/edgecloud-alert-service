package com.edgecloud.alert.dto;

import java.time.Instant;
import java.util.UUID;

import com.edgecloud.alert.entity.AlertEventOwnershipAction;
import com.edgecloud.alert.entity.AlertEventOwnershipHistory;

public record AlertEventOwnershipHistoryResponse(
        UUID id,
        UUID alertId,
        UUID projectId,
        AlertEventOwnershipAction action,
        UUID actorUserId,
        UUID ownerUserId,
        String ownerDisplayName,
        Instant changedAt) {

    public static AlertEventOwnershipHistoryResponse from(AlertEventOwnershipHistory history) {
        return new AlertEventOwnershipHistoryResponse(
                history.getId(), history.getAlertEventId(), history.getProjectId(), history.getAction(),
                history.getActorUserId(), history.getOwnerUserId(), history.getOwnerDisplayName(),
                history.getChangedAt());
    }
}
