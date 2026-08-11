package com.edgecloud.alert.client;

public interface NotificationLifecycleClient {
    AlertLifecycleNotificationResponse publish(AlertLifecycleNotificationRequest request);
}
