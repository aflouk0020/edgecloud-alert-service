package com.edgecloud.alert.service;

import com.edgecloud.alert.dto.NotificationResponse;
import com.edgecloud.alert.entity.Alert;
import com.edgecloud.alert.entity.Notification;
import com.edgecloud.alert.entity.NotificationStatus;
import com.edgecloud.alert.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public NotificationResponse prepareNotification(Alert alert) {

        return notificationRepository.findFirstByAlertId(alert.getId())
                .map(this::toResponse)
                .orElseGet(() -> {

                    Notification notification = new Notification();

                    notification.setAlertId(alert.getId());
                    notification.setAlertType(alert.getAlertType());
                    notification.setSeverity(alert.getSeverity());
                    notification.setMessage(alert.getMessage());
                    notification.setSourceService(alert.getSourceService());

                    Notification saved =
                            notificationRepository.save(notification);

                    return toResponse(saved);
                });
    }


    @Override
    public List<NotificationResponse> getReadyNotifications() {

        return notificationRepository
                .findByStatusOrderByCreatedAtDesc(
                        NotificationStatus.READY
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }


    @Override
    public long getReadyNotificationCount() {

        return notificationRepository.countByStatus(
                NotificationStatus.READY
        );
    }


    private NotificationResponse toResponse(Notification notification) {

        return new NotificationResponse(
                notification.getId(),
                notification.getAlertId(),
                notification.getAlertType(),
                notification.getSeverity(),
                notification.getMessage(),
                notification.getSourceService(),
                notification.getStatus(),
                notification.getCreatedAt()
        );
    }
}
