package com.edgecloud.alert.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("edgecloud.notification")
public record NotificationServiceProperties(
        String baseUrl,
        String internalServiceKey,
        boolean publisherEnabled,
        Duration pollInterval,
        int batchSize,
        Duration staleTimeout,
        Duration connectTimeout,
        Duration readTimeout) {

    public NotificationServiceProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost:8085";
        if (internalServiceKey == null) internalServiceKey = "";
        if (pollInterval == null) pollInterval = Duration.ofSeconds(5);
        if (batchSize <= 0) batchSize = 50;
        if (staleTimeout == null) staleTimeout = Duration.ofMinutes(5);
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(2);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(5);
    }
}
