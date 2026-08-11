package com.edgecloud.alert.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.edgecloud.alert.config.NotificationServiceProperties;
import com.edgecloud.alert.entity.NotificationLifecycleEventType;
import com.edgecloud.alert.entity.Severity;
import com.sun.net.httpserver.HttpServer;

class NotificationLifecycleClientImplTest {
    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> header = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private AlertLifecycleNotificationRequest request;
    private NotificationLifecycleClientImpl client;

    @BeforeEach
    void setUp() throws IOException {
        UUID sourceEventId = UUID.randomUUID();
        request = new AlertLifecycleNotificationRequest(sourceEventId, UUID.randomUUID(), UUID.randomUUID(),
                NotificationLifecycleEventType.OPENED, Severity.HIGH, "CPU overload", "CPU_USAGE", "DEVICE",
                "device-1", new BigDecimal("95.25"), new BigDecimal("80.00"),
                Instant.parse("2026-08-11T10:00:00Z"));
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/notifications/alert-events", exchange -> {
            header.set(exchange.getRequestHeaders().getFirst("X-Internal-Service-Key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int responseStatus = status.get();
            byte[] response = responseStatus == 200
                    ? ("{\"sourceEventId\":\"" + sourceEventId
                       + "\",\"recipientsResolved\":1,\"notificationsCreated\":1,"
                       + "\"deliveriesCreated\":1,\"duplicateReplay\":true}").getBytes(StandardCharsets.UTF_8)
                    : "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        client = new NotificationLifecycleClientImpl(RestClient.builder(),
                new NotificationServiceProperties("http://localhost:" + server.getAddress().getPort(), "secret-key",
                        true, Duration.ofSeconds(5), 50, Duration.ofMinutes(5),
                        Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsExactPathHeaderAndPayloadAndAcceptsDuplicateReplay() {
        var response = client.publish(request);
        assertThat(response.duplicateReplay()).isTrue();
        assertThat(header.get()).isEqualTo("secret-key");
        assertThat(body.get()).contains("\"sourceEventId\":\"" + request.sourceEventId() + "\"")
                .contains("\"eventType\":\"OPENED\"")
                .contains("\"thresholdValue\":80.00");
    }

    @Test
    void classifiesValidationAndServerFailures() {
        status.set(400);
        assertThatThrownBy(() -> client.publish(request)).isInstanceOfSatisfying(
                NotificationLifecycleClientException.class, ex -> assertThat(ex.isRetryable()).isFalse());
        status.set(503);
        assertThatThrownBy(() -> client.publish(request)).isInstanceOfSatisfying(
                NotificationLifecycleClientException.class, ex -> assertThat(ex.isRetryable()).isTrue());
    }
}
