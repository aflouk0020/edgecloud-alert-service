package com.edgecloud.alert.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edgecloud.alert.entity.AlertNotificationOutbox;
import com.edgecloud.alert.entity.AlertNotificationOutboxStatus;
import com.edgecloud.alert.repository.AlertNotificationOutboxRepository;

@Service
public class AlertNotificationOutboxTransactions {
    private static final int MAX_ATTEMPTS = 5;
    private static final List<Duration> BACKOFF = List.of(
            Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofMinutes(30));

    private final AlertNotificationOutboxRepository repository;

    public AlertNotificationOutboxTransactions(AlertNotificationOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<AlertNotificationOutbox> claim(Instant now, int batchSize) {
        List<AlertNotificationOutbox> claimed = repository.lockEligible(now, PageRequest.of(0, batchSize));
        claimed.forEach(item -> item.claim(now));
        return repository.saveAll(claimed);
    }

    @Transactional
    public int recoverStale(Instant now, Duration staleTimeout) {
        List<AlertNotificationOutbox> stale = repository.findByStatusAndProcessingStartedAtBefore(
                AlertNotificationOutboxStatus.PROCESSING, now.minus(staleTimeout));
        stale.forEach(item -> {
            if (item.getAttemptCount() >= MAX_ATTEMPTS) {
                item.fail(now, "STALE_PROCESSING", "Publisher did not complete the claimed attempt");
            } else {
                item.retry(now, now, "STALE_PROCESSING", "Publisher did not complete the claimed attempt");
            }
        });
        repository.saveAll(stale);
        return stale.size();
    }

    @Transactional
    public void recordPublished(UUID id, Instant now) {
        repository.findById(id).filter(item -> item.getStatus() == AlertNotificationOutboxStatus.PROCESSING)
                .ifPresent(item -> item.publish(now));
    }

    @Transactional
    public void recordFailure(UUID id, Instant now, boolean retryable, String category, String message) {
        repository.findById(id).filter(item -> item.getStatus() == AlertNotificationOutboxStatus.PROCESSING)
                .ifPresent(item -> {
                    if (!retryable || item.getAttemptCount() >= MAX_ATTEMPTS) {
                        item.fail(now, category, message);
                    } else {
                        Duration delay = BACKOFF.get(item.getAttemptCount() - 1);
                        item.retry(now, now.plus(delay), category, message);
                    }
                });
    }
}
