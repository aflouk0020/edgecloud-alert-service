package com.edgecloud.alert.controller;

import com.edgecloud.alert.dto.NotificationResponse;
import com.edgecloud.alert.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(
            NotificationService notificationService) {
        this.notificationService = notificationService;
    }


    @GetMapping
    public List<NotificationResponse> getReadyNotifications() {
        return notificationService.getReadyNotifications();
    }


    @GetMapping("/count")
    public long getReadyNotificationCount() {
        return notificationService.getReadyNotificationCount();
    }
}
