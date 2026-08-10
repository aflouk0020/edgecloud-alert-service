package com.edgecloud.alert.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.edgecloud.alert.dto.AlertEventFilter;
import com.edgecloud.alert.dto.AlertEventResponse;
import com.edgecloud.alert.entity.AlertEventSourceType;
import com.edgecloud.alert.entity.AlertEventStatus;
import com.edgecloud.alert.entity.AlertRuleComparisonOperator;
import com.edgecloud.alert.entity.AlertRuleMetricType;
import com.edgecloud.alert.entity.Severity;
import com.edgecloud.alert.evaluation.AlertEvaluationResult;
import com.edgecloud.alert.evaluation.AlertEvaluationSourceType;
import com.edgecloud.alert.repository.AlertEventRepository;
import com.edgecloud.alert.repository.AlertEventSpecifications;

@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.show-sql=false",
        "spring.flyway.enabled=true",
        "debug=false",
        "logging.level.org.hibernate.SQL=OFF",
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false"
})
class AlertEventMySqlIntegrationTest {

    private static final long TARGET_MILLIS = 1_000;
    private static final int MEASURED_RUNS = 5;
    private static final UUID PROJECT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RULE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final String SOURCE_ID = "mysql-device-1";
    private static final Instant BASE_TIME = Instant.parse("2026-08-10T12:00:00Z");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("alert_service_test")
            .withUsername("alert_test")
            .withPassword("alert_test");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private AlertEventGenerationService generationService;

    @Autowired
    private AlertEventQueryService queryService;

    @Autowired
    private AlertEventRepository repository;

    @BeforeEach
    void clearEvents() {
        repository.deleteAll();
    }

    @Test
    void concurrentIdenticalTriggersProduceExactlyOneValidOpenEvent() throws Exception {
        int requestCount = 20;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(requestCount)) {
            List<Future<AlertEventResponse>> futures = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return generationService.process(result(RULE_ID, true, BASE_TIME)).orElseThrow();
                }));
            }
            ready.await();
            start.countDown();

            List<AlertEventResponse> responses = new ArrayList<>();
            for (Future<AlertEventResponse> future : futures) responses.add(future.get());

            assertThat(responses).hasSize(requestCount);
            assertThat(responses).extracting(AlertEventResponse::alertId)
                    .containsOnly(responses.getFirst().alertId());
        }

        var events = repository.findAll(AlertEventSpecifications.forProject(PROJECT_ID, null));
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getStatus()).isEqualTo(AlertEventStatus.OPEN);
        assertThat(events.getFirst().getOpenMarker()).isEqualTo(1);
        assertThat(events.getFirst().getLastObservedAt()).isEqualTo(BASE_TIME);
        assertThat(events.getFirst().getResolvedAt()).isNull();
    }

    @Test
    void repeatedTriggerResolutionAndRetriggerPreserveHistoryAndNullableMarkerSemantics() {
        AlertEventResponse first = generationService.process(result(RULE_ID, true, BASE_TIME)).orElseThrow();
        Instant repeatedAt = BASE_TIME.plusSeconds(60);
        AlertEventResponse repeated = generationService.process(result(RULE_ID, true, repeatedAt)).orElseThrow();
        assertThat(repeated.alertId()).isEqualTo(first.alertId());
        assertThat(repeated.lastObservedAt()).isEqualTo(repeatedAt);
        assertThat(repository.count()).isEqualTo(1);

        Instant resolvedAt = BASE_TIME.plusSeconds(120);
        AlertEventResponse resolved = generationService.process(result(RULE_ID, false, resolvedAt)).orElseThrow();
        assertThat(resolved.alertId()).isEqualTo(first.alertId());
        assertThat(resolved.status()).isEqualTo(AlertEventStatus.RESOLVED);
        assertThat(resolved.resolvedAt()).isEqualTo(resolvedAt);

        Instant retriggeredAt = BASE_TIME.plusSeconds(180);
        AlertEventResponse retriggered = generationService.process(result(RULE_ID, true, retriggeredAt)).orElseThrow();
        assertThat(retriggered.alertId()).isNotEqualTo(first.alertId());
        assertThat(retriggered.status()).isEqualTo(AlertEventStatus.OPEN);

        // Resolve the second event and create a third OPEN event to prove multiple NULL markers coexist.
        generationService.process(result(RULE_ID, false, BASE_TIME.plusSeconds(240))).orElseThrow();
        AlertEventResponse third = generationService.process(
                result(RULE_ID, true, BASE_TIME.plusSeconds(300))).orElseThrow();

        var history = repository.findAll(AlertEventSpecifications.forProject(PROJECT_ID, null));
        assertThat(history).hasSize(3);
        assertThat(history).filteredOn(event -> event.getStatus() == AlertEventStatus.RESOLVED).hasSize(2)
                .allSatisfy(event -> assertThat(event.getOpenMarker()).isNull());
        assertThat(history).filteredOn(event -> event.getStatus() == AlertEventStatus.OPEN).hasSize(1)
                .first().extracting(event -> event.getId()).isEqualTo(third.alertId());

        var unchangedFirst = repository.findById(first.alertId()).orElseThrow();
        assertThat(unchangedFirst.getResolvedAt()).isEqualTo(resolvedAt);
        assertThat(unchangedFirst.getLastObservedAt()).isEqualTo(resolvedAt);
    }

    @Test
    void boundedLifecycleAndFilteredPageOperationsMeetControlledLocalTarget() {
        measure("new OPEN generation", iteration -> {
            UUID ruleId = ruleId(10 + iteration);
            AlertEventResponse response = generationService.process(
                    result(ruleId, true, BASE_TIME.plusSeconds(iteration))).orElseThrow();
            assertThat(response.status()).isEqualTo(AlertEventStatus.OPEN);
        });

        UUID updateRule = ruleId(30);
        UUID updateId = generationService.process(result(updateRule, true, BASE_TIME)).orElseThrow().alertId();
        measure("existing OPEN update", iteration -> {
            Instant observedAt = BASE_TIME.plusSeconds(10 + iteration);
            AlertEventResponse response = generationService.process(result(updateRule, true, observedAt)).orElseThrow();
            assertThat(response.alertId()).isEqualTo(updateId);
            assertThat(response.lastObservedAt()).isEqualTo(observedAt);
        });

        measure("OPEN resolution", iteration -> {
            UUID ruleId = ruleId(50 + iteration);
            generationService.process(result(ruleId, true, BASE_TIME.plusSeconds(iteration))).orElseThrow();
            AlertEventResponse response = generationService.process(
                    result(ruleId, false, BASE_TIME.plusSeconds(100 + iteration))).orElseThrow();
            assertThat(response.status()).isEqualTo(AlertEventStatus.RESOLVED);
            assertThat(response.resolvedAt()).isNotNull();
        });

        seedResolvedHistory(50);
        AlertEventFilter filter = new AlertEventFilter(AlertEventStatus.RESOLVED, Severity.HIGH,
                AlertEventSourceType.DEVICE, SOURCE_ID, BASE_TIME.minusSeconds(1), BASE_TIME.plusSeconds(10_000));
        measure("filtered project page query", iteration -> {
            var page = queryService.list(PROJECT_ID, filter, 0, 50, "DESC");
            assertThat(page.alerts()).hasSize(50);
            assertThat(page.totalElements()).isGreaterThanOrEqualTo(50);
        });
    }

    private void seedResolvedHistory(int count) {
        for (int index = 0; index < count; index++) {
            UUID ruleId = ruleId(100 + index);
            Instant triggeredAt = BASE_TIME.plusSeconds(1_000 + index * 2L);
            generationService.process(result(ruleId, true, triggeredAt)).orElseThrow();
            generationService.process(result(ruleId, false, triggeredAt.plusSeconds(1))).orElseThrow();
        }
    }

    private void measure(String operation, CheckedOperation operationCall) {
        operationCall.run(-1); // warm-up
        double[] durations = new double[MEASURED_RUNS];
        for (int iteration = 0; iteration < MEASURED_RUNS; iteration++) {
            long started = System.nanoTime();
            operationCall.run(iteration);
            durations[iteration] = (System.nanoTime() - started) / 1_000_000.0;
            assertThat(durations[iteration]).as(operation + " run " + (iteration + 1)).isLessThan(TARGET_MILLIS);
        }
        double[] sorted = durations.clone();
        Arrays.sort(sorted);
        System.out.printf("SCRUM-703 MySQL %s durations(ms)=%s min=%.3f max=%.3f average=%.3f median=%.3f%n",
                operation, Arrays.toString(durations), sorted[0], sorted[sorted.length - 1],
                Arrays.stream(durations).average().orElseThrow(), sorted[sorted.length / 2]);
    }

    private AlertEvaluationResult result(UUID ruleId, boolean triggered, Instant evaluatedAt) {
        return new AlertEvaluationResult(ruleId, "MySQL CPU rule", PROJECT_ID,
                AlertEvaluationSourceType.DEVICE, SOURCE_ID, AlertRuleMetricType.CPU_USAGE,
                triggered ? new BigDecimal("91.500000") : new BigDecimal("40.000000"),
                new BigDecimal("80.000000"), AlertRuleComparisonOperator.GREATER_THAN,
                Severity.HIGH, triggered, evaluatedAt);
    }

    private UUID ruleId(int suffix) {
        return UUID.fromString("20000000-0000-0000-0000-" + String.format("%012d", suffix));
    }

    @FunctionalInterface
    private interface CheckedOperation {
        void run(int iteration);
    }
}
