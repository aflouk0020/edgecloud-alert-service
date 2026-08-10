package com.edgecloud.alert.dto;

import java.time.Instant;
import java.util.UUID;

import com.edgecloud.alert.entity.AlertEventSourceType;
import com.edgecloud.alert.entity.AlertEventStatus;
import com.edgecloud.alert.entity.Severity;

public record AlertEventFilter(
        AlertEventStatus status,
        Severity severity,
        AlertEventSourceType sourceType,
        String sourceId,
        UUID ownerId,
        Instant from,
        Instant to) {
}
