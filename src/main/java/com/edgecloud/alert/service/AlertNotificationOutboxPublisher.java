package com.edgecloud.alert.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.edgecloud.alert.client.AlertLifecycleNotificationRequest;
import com.edgecloud.alert.client.NotificationLifecycleClient;
import com.edgecloud.alert.client.NotificationLifecycleClientException;
import com.edgecloud.alert.config.NotificationServiceProperties;
import com.edgecloud.alert.entity.AlertNotificationOutbox;

@Component
@ConditionalOnProperty(prefix = "edgecloud.notification", name = "publisher-enabled",
        havingValue = "true", matchIfMissing = true)
public class AlertNotificationOutboxPublisher {
    private final AlertNotificationOutboxTransactions transactions;
    private final NotificationLifecycleClient client;
    private final NotificationServiceProperties properties;
    private final Clock clock;

    @Autowired
    public AlertNotificationOutboxPublisher(AlertNotificationOutboxTransactions transactions,
                                            NotificationLifecycleClient client,
                                            NotificationServiceProperties properties) {
        this(transactions, client, properties, Clock.systemUTC());
    }

    AlertNotificationOutboxPublisher(AlertNotificationOutboxTransactions transactions,
                                     NotificationLifecycleClient client,
                                     NotificationServiceProperties properties,
                                     Clock clock) {
        this.transactions = transactions;
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${edgecloud.notification.poll-interval:5s}")
    public void publishDue() {
        Instant now = clock.instant();
        transactions.recoverStale(now, properties.staleTimeout());
        List<AlertNotificationOutbox> claimed = transactions.claim(now, properties.batchSize());
        for (AlertNotificationOutbox item : claimed) {
            try {
                client.publish(AlertLifecycleNotificationRequest.from(item));
                transactions.recordPublished(item.getId(), clock.instant());
            } catch (NotificationLifecycleClientException ex) {
                transactions.recordFailure(item.getId(), clock.instant(), ex.isRetryable(),
                        ex.getCategory(), ex.getMessage());
            } catch (RuntimeException ex) {
                transactions.recordFailure(item.getId(), clock.instant(), true,
                        "UNEXPECTED_CLIENT_FAILURE", ex.getMessage());
            }
        }
    }
}
