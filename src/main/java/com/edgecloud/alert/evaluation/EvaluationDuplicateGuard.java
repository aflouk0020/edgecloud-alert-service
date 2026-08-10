package com.edgecloud.alert.evaluation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EvaluationDuplicateGuard {

    private final int maximumSize;
    private final Duration ttl;
    private final Clock clock;
    private final Map<String, Instant> seen = new LinkedHashMap<>();

    @Autowired
    public EvaluationDuplicateGuard(
            @Value("${edgecloud.alert.evaluation.duplicate-maximum-size:10000}") int maximumSize,
            @Value("${edgecloud.alert.evaluation.duplicate-ttl-seconds:30}") long ttlSeconds) {
        this(maximumSize, Duration.ofSeconds(ttlSeconds), Clock.systemUTC());
    }

    EvaluationDuplicateGuard(int maximumSize, Duration ttl, Clock clock) {
        if (maximumSize < 1 || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("Duplicate guard size and TTL must be positive");
        }
        this.maximumSize = maximumSize;
        this.ttl = ttl;
        this.clock = clock;
    }

    public synchronized boolean isDuplicate(AlertEvaluationInput input) {
        Instant now = clock.instant();
        evictExpired(now);
        String key = key(input);
        Instant previous = seen.putIfAbsent(key, now);
        if (previous != null) {
            return true;
        }
        while (seen.size() > maximumSize) {
            seen.remove(seen.keySet().iterator().next());
        }
        return false;
    }

    private void evictExpired(Instant now) {
        seen.entrySet().removeIf(entry -> entry.getValue().plus(ttl).isBefore(now));
    }

    private String key(AlertEvaluationInput input) {
        return input.projectId() + ":" + input.sourceType() + ":" + input.sourceId()
                + ":" + input.metricType() + ":" + input.sampleId();
    }
}