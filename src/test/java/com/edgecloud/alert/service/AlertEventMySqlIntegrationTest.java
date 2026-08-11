package com.edgecloud.alert.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

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
import com.edgecloud.alert.entity.AlertEventOwnershipAction;
import com.edgecloud.alert.entity.AlertRuleComparisonOperator;
import com.edgecloud.alert.entity.AlertRuleMetricType;
import com.edgecloud.alert.entity.Severity;
import com.edgecloud.alert.evaluation.AlertEvaluationResult;
import com.edgecloud.alert.evaluation.AlertEvaluationSourceType;
import com.edgecloud.alert.repository.AlertEventRepository;
import com.edgecloud.alert.repository.AlertEventOwnershipHistoryRepository;
import com.edgecloud.alert.repository.AlertNotificationOutboxRepository;
import com.edgecloud.alert.entity.NotificationLifecycleEventType;
import com.edgecloud.alert.entity.AlertNotificationOutboxStatus;
import com.edgecloud.alert.client.AlertLifecycleNotificationResponse;
import com.edgecloud.alert.client.NotificationLifecycleClientException;
import com.edgecloud.alert.config.NotificationServiceProperties;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.edgecloud.alert.repository.AlertEventSpecifications;
import com.edgecloud.alert.exception.AlertOwnershipConflictException;

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

    @Autowired
    private AlertEventOwnershipHistoryRepository historyRepository;

    @Autowired
    private AlertOwnershipService ownershipService;

    @Autowired
    private AlertNotificationOutboxRepository outboxRepository;

    @Autowired
    private AlertNotificationOutboxTransactions outboxTransactions;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearEvents() {
        historyRepository.deleteAll();
        outboxRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    void acknowledgedAlertRemainsUniqueActiveAndResolvesWithoutLosingOwnership() {
        UUID ownerId = UUID.randomUUID();
        AlertEventResponse open = generationService.process(result(RULE_ID, true, BASE_TIME)).orElseThrow();
        AlertEventResponse acknowledged = ownershipService.acknowledge(
                PROJECT_ID, open.alertId(), ownerId, "engineer@example.com");
        assertThat(acknowledged.status()).isEqualTo(AlertEventStatus.ACKNOWLEDGED);
        assertThat(repository.findById(open.alertId()).orElseThrow().getOpenMarker()).isEqualTo(1);

        AlertEventResponse repeated = generationService.process(
                result(RULE_ID, true, BASE_TIME.plusSeconds(60))).orElseThrow();
        assertThat(repeated.alertId()).isEqualTo(open.alertId());
        assertThat(repeated.status()).isEqualTo(AlertEventStatus.ACKNOWLEDGED);
        assertThat(repeated.ownerUserId()).isEqualTo(ownerId);
        assertThat(repository.count()).isEqualTo(1);

        AlertEventResponse resolved = generationService.process(
                result(RULE_ID, false, BASE_TIME.plusSeconds(120))).orElseThrow();
        assertThat(resolved.status()).isEqualTo(AlertEventStatus.RESOLVED);
        assertThat(resolved.ownerUserId()).isEqualTo(ownerId);
        assertThat(resolved.acknowledgedAt()).isEqualTo(acknowledged.acknowledgedAt());

        AlertEventResponse retriggered = generationService.process(
                result(RULE_ID, true, BASE_TIME.plusSeconds(180))).orElseThrow();
        assertThat(retriggered.alertId()).isNotEqualTo(open.alertId());
        assertThat(retriggered.status()).isEqualTo(AlertEventStatus.OPEN);
        assertThat(retriggered.ownerUserId()).isNull();
        assertThat(retriggered.acknowledgedAt()).isNull();
    }

    @Test
    void ownershipHistoryIsProjectScopedAndDeterministicallyOrdered() {
        UUID ownerId = UUID.randomUUID();
        AlertEventResponse open = generationService.process(result(RULE_ID, true, BASE_TIME)).orElseThrow();
        ownershipService.acknowledge(PROJECT_ID, open.alertId(), ownerId, "engineer@example.com");
        ownershipService.release(PROJECT_ID, open.alertId(), ownerId);

        var history = historyRepository.findByProjectIdAndAlertEventIdOrderByChangedAtAscIdAsc(
                PROJECT_ID, open.alertId());
        assertThat(history).extracting(item -> item.getAction())
                .containsExactly(AlertEventOwnershipAction.ACKNOWLEDGED, AlertEventOwnershipAction.RELEASED);
        assertThat(historyRepository.findByProjectIdAndAlertEventIdOrderByChangedAtAscIdAsc(
                UUID.randomUUID(), open.alertId())).isEmpty();
    }

    @Test
    void concurrentDifferentOwnersCannotSilentlyTakeOwnership() throws Exception {
        AlertEventResponse open = generationService.process(result(RULE_ID, true, BASE_TIME)).orElseThrow();
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<AlertEventResponse>> attempts = List.of(
                    executor.submit(() -> acknowledgeAfterLatch(open.alertId(), firstOwner, ready, start)),
                    executor.submit(() -> acknowledgeAfterLatch(open.alertId(), secondOwner, ready, start)));
            ready.await();
            start.countDown();

            int successes = 0;
            int conflicts = 0;
            for (Future<AlertEventResponse> attempt : attempts) {
                try {
                    AlertEventResponse response = attempt.get();
                    assertThat(response.status()).isEqualTo(AlertEventStatus.ACKNOWLEDGED);
                    successes++;
                } catch (ExecutionException ex) {
                    assertThat(ex.getCause()).isInstanceOf(AlertOwnershipConflictException.class);
                    conflicts++;
                }
            }
            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
        }

        var persisted = repository.findById(open.alertId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(AlertEventStatus.ACKNOWLEDGED);
        assertThat(persisted.getOwnerUserId()).isIn(firstOwner, secondOwner);
        assertThat(historyRepository.findByProjectIdAndAlertEventIdOrderByChangedAtAscIdAsc(
                PROJECT_ID, open.alertId())).hasSize(1);
    }

    private AlertEventResponse acknowledgeAfterLatch(UUID alertId, UUID ownerId,
                                                      CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        return ownershipService.acknowledge(PROJECT_ID, alertId, ownerId, ownerId.toString());
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
        assertThat(outboxRepository.countByAlertEventIdAndEventType(
                events.getFirst().getId(), NotificationLifecycleEventType.OPENED)).isEqualTo(1);
    }

    @Test
    void concurrentPublisherClaimsReturnOneCopyOfOneOutboxRow() throws Exception {
        generationService.process(result(RULE_ID, true, BASE_TIME)).orElseThrow();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> claims = List.of(
                    executor.submit(() -> claimAfterLatch(ready, start)),
                    executor.submit(() -> claimAfterLatch(ready, start)));
            ready.await();
            start.countDown();
            assertThat(claims.get(0).get() + claims.get(1).get()).isEqualTo(1);
        }
        assertThat(outboxRepository.findAll()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getAttemptCount()).isEqualTo(1);
                    assertThat(item.getSourceEventId()).isEqualTo(item.getId());
                });
    }

    @Test
    void lifecycleAndOutboxRollbackTogether() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transaction.executeWithoutResult(ignored -> {
            generationService.process(result(RULE_ID, true, BASE_TIME)).orElseThrow();
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(repository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void staleProcessingRecoveryPreservesSnapshotAndAttemptCount() {
        AlertEventResponse open = generationService.process(result(RULE_ID, true, BASE_TIME)).orElseThrow();
        UUID sourceEventId = outboxRepository.findAll().getFirst().getSourceEventId();
        assertThat(outboxTransactions.claim(BASE_TIME.plusSeconds(10), 1)).hasSize(1);
        assertThat(outboxTransactions.recoverStale(BASE_TIME.plusSeconds(311), Duration.ofMinutes(5))).isEqualTo(1);
        assertThat(outboxRepository.findAll()).singleElement().satisfies(item -> {
            assertThat(item.getStatus()).isEqualTo(AlertNotificationOutboxStatus.RETRY_SCHEDULED);
            assertThat(item.getAttemptCount()).isEqualTo(1);
            assertThat(item.getSourceEventId()).isEqualTo(sourceEventId);
            assertThat(item.getAlertEventId()).isEqualTo(open.alertId());
        });
    }

    @Test
    void notificationFailureDoesNotRollbackOpenedOrResolvedAndLaterSuccessPublishes() {
        AlertEventResponse open = generationService.process(result(RULE_ID, true, BASE_TIME)).orElseThrow();
        Instant publishAt = BASE_TIME.plusSeconds(600);
        publisher(request -> { throw new NotificationLifecycleClientException(
                true, "CONNECTION", "unavailable", null); }, publishAt).publishDue();

        assertThat(repository.findById(open.alertId())).isPresent();
        assertThat(outboxRepository.findAll()).singleElement()
                .satisfies(item -> assertThat(item.getStatus())
                        .isEqualTo(AlertNotificationOutboxStatus.RETRY_SCHEDULED));
        publisher(request -> new AlertLifecycleNotificationResponse(
                request.sourceEventId(), 1, 1, 1, true), publishAt.plusSeconds(31)).publishDue();
        assertThat(outboxRepository.findAll()).singleElement()
                .satisfies(item -> assertThat(item.getStatus()).isEqualTo(AlertNotificationOutboxStatus.PUBLISHED));

        generationService.process(result(RULE_ID, false, BASE_TIME.plusSeconds(700))).orElseThrow();
        publisher(request -> { throw new NotificationLifecycleClientException(
                true, "HTTP_503", "unavailable", null); }, publishAt.plusSeconds(101)).publishDue();
        assertThat(repository.findById(open.alertId()).orElseThrow().getStatus()).isEqualTo(AlertEventStatus.RESOLVED);
        assertThat(outboxRepository.findAll()).filteredOn(item ->
                item.getEventType() == NotificationLifecycleEventType.RESOLVED).singleElement()
                .satisfies(item -> assertThat(item.getStatus())
                        .isEqualTo(AlertNotificationOutboxStatus.RETRY_SCHEDULED));
        publisher(request -> new AlertLifecycleNotificationResponse(
                request.sourceEventId(), 1, 1, 1, false), publishAt.plusSeconds(132)).publishDue();
        assertThat(outboxRepository.findAll()).allSatisfy(item ->
                assertThat(item.getStatus()).isEqualTo(AlertNotificationOutboxStatus.PUBLISHED));
    }

    @Test
    void acknowledgementReplayReleaseAndReackProduceOnlyGenuineAcknowledgedEvents() {
        UUID ownerId = UUID.randomUUID();
        AlertEventResponse open = generationService.process(result(RULE_ID, true, BASE_TIME)).orElseThrow();
        ownershipService.acknowledge(PROJECT_ID, open.alertId(), ownerId, "owner");
        ownershipService.acknowledge(PROJECT_ID, open.alertId(), ownerId, "ignored replay label");
        assertThat(outboxRepository.countByAlertEventIdAndEventType(
                open.alertId(), NotificationLifecycleEventType.ACKNOWLEDGED)).isEqualTo(1);
        ownershipService.release(PROJECT_ID, open.alertId(), ownerId);
        assertThat(outboxRepository.count()).isEqualTo(2);
        ownershipService.acknowledge(PROJECT_ID, open.alertId(), ownerId, "owner");
        assertThat(outboxRepository.countByAlertEventIdAndEventType(
                open.alertId(), NotificationLifecycleEventType.ACKNOWLEDGED)).isEqualTo(2);
    }

    private AlertNotificationOutboxPublisher publisher(
            com.edgecloud.alert.client.NotificationLifecycleClient client, Instant instant) {
        return new AlertNotificationOutboxPublisher(outboxTransactions, client,
                new NotificationServiceProperties("http://localhost", "key", true, Duration.ofSeconds(5),
                        50, Duration.ofMinutes(5), Duration.ofSeconds(1), Duration.ofSeconds(1)),
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    private int claimAfterLatch(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return outboxTransactions.claim(Instant.now(), 1).size();
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
                AlertEventSourceType.DEVICE, SOURCE_ID, null,
                BASE_TIME.minusSeconds(1), BASE_TIME.plusSeconds(10_000));
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
