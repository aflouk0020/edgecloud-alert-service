package com.edgecloud.alert.service;

import java.util.UUID;
import java.util.List;

import com.edgecloud.alert.dto.AlertEventResponse;
import com.edgecloud.alert.dto.AlertEventOwnershipHistoryResponse;

public interface AlertOwnershipService {
    AlertEventResponse acknowledge(UUID projectId, UUID alertId, UUID actorUserId, String ownerLabel);
    AlertEventResponse release(UUID projectId, UUID alertId, UUID actorUserId);
    List<AlertEventOwnershipHistoryResponse> history(UUID projectId, UUID alertId);
}
