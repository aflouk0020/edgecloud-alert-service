package com.edgecloud.alert.client;

import java.util.UUID;

public record AlertLifecycleNotificationResponse(
        UUID sourceEventId,
        long recipientsResolved,
        long notificationsCreated,
        long deliveriesCreated,
        boolean duplicateReplay) {
}
