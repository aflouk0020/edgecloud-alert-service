package com.edgecloud.alert.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.edgecloud.alert.client.AlertLifecycleNotificationRequest;
import com.edgecloud.alert.client.AlertLifecycleNotificationResponse;
import com.edgecloud.alert.client.NotificationLifecycleClient;
import com.edgecloud.alert.client.NotificationLifecycleClientException;
import com.edgecloud.alert.config.NotificationServiceProperties;
import com.edgecloud.alert.entity.AlertNotificationOutbox;

class AlertNotificationOutboxPublisherTest {

    @Test
    void publishesWholeClaimedBatchAndIsolatesTransientFailure() {
        AlertNotificationOutboxTransactions transactions = mock(AlertNotificationOutboxTransactions.class);
        NotificationLifecycleClient client = mock(NotificationLifecycleClient.class);
        AlertNotificationOutbox first = item();
        AlertNotificationOutbox second = item();
        when(transactions.claim(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(50)))
                .thenReturn(List.of(first, second));
        when(client.publish(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            AlertLifecycleNotificationRequest request = invocation.getArgument(0);
            if (request.sourceEventId().equals(first.getSourceEventId())) {
                throw new NotificationLifecycleClientException(true, "HTTP_503", "unavailable", null);
            }
            return new AlertLifecycleNotificationResponse(second.getSourceEventId(), 1, 1, 1, true);
        });

        publisher(transactions, client).publishDue();

        verify(transactions).recordFailure(org.mockito.ArgumentMatchers.eq(first.getId()),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq("HTTP_503"), org.mockito.ArgumentMatchers.eq("unavailable"));
        verify(transactions).recordPublished(org.mockito.ArgumentMatchers.eq(second.getId()),
                org.mockito.ArgumentMatchers.any());
        InOrder order = inOrder(transactions, client);
        order.verify(transactions).claim(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(50));
        order.verify(client).publish(org.mockito.ArgumentMatchers.any(AlertLifecycleNotificationRequest.class));
    }

    private AlertNotificationOutboxPublisher publisher(AlertNotificationOutboxTransactions transactions,
                                                        NotificationLifecycleClient client) {
        return new AlertNotificationOutboxPublisher(transactions, client,
                new NotificationServiceProperties("http://localhost", "key", true, Duration.ofSeconds(5),
                        50, Duration.ofMinutes(5), Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    private AlertNotificationOutbox item() {
        AlertNotificationOutbox item = mock(AlertNotificationOutbox.class);
        UUID id = UUID.randomUUID();
        when(item.getId()).thenReturn(id);
        when(item.getSourceEventId()).thenReturn(id);
        when(item.getAlertEventId()).thenReturn(UUID.randomUUID());
        when(item.getProjectId()).thenReturn(UUID.randomUUID());
        when(item.getOccurredAt()).thenReturn(Instant.now());
        return item;
    }
}
