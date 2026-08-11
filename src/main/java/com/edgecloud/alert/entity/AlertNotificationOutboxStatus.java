package com.edgecloud.alert.entity;

public enum AlertNotificationOutboxStatus {
    PENDING,
    PROCESSING,
    RETRY_SCHEDULED,
    PUBLISHED,
    FAILED
}
