package com.edgecloud.alert.dto;

import java.time.Instant;

import com.edgecloud.alert.entity.AlertEventSourceType;
import com.edgecloud.alert.entity.AlertEventStatus;
import com.edgecloud.alert.entity.Severity;

public record AlertEventFilter(
        AlertEventStatus status,
        Severity severity,
        AlertEventSourceType sourceType,
        String sourceId,
        Instant from,
        Instant to) {
}
