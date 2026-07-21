package com.edgecloud.alert.service;

import com.edgecloud.alert.dto.NotificationResponse;
import com.edgecloud.alert.entity.Alert;

import java.util.List;

public interface NotificationService {

    NotificationResponse prepareNotification(Alert alert);

    List<NotificationResponse> getReadyNotifications();

    long getReadyNotificationCount();

}
