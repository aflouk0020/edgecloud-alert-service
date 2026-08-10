package com.edgecloud.alert.client;

import java.util.List;
import java.util.UUID;

public record ProjectWorkspaceResponse(
        UUID projectId,
        String projectName,
        String projectStatus,
        UUID callerUserId,
        String callerProjectRole,
        List<UUID> serviceIds,
        List<String> deviceIds) {
}