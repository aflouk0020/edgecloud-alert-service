package com.edgecloud.alert.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import com.edgecloud.alert.entity.AlertRule;
import com.edgecloud.alert.entity.AlertRuleComparisonOperator;
import com.edgecloud.alert.entity.AlertRuleMetricType;
import com.edgecloud.alert.entity.Severity;
import com.edgecloud.alert.exception.AlertEvaluationValidationException;
import com.edgecloud.alert.repository.AlertRuleRepository;

@ExtendWith(MockitoExtension.class)
class AlertRuleEvaluationServiceImplTest {

    private static final int PERFORMANCE_RULE_COUNT = 500;
    private static final int PERFORMANCE_MEASURED_RUNS = 5;
    private static final long PERFORMANCE_TARGET_MILLIS = 1_000;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OTHER_PROJECT_ID = UUID.randomUUID();
    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final UUID RULE_ID = UUID.randomUUID();

    @Mock
    private AlertRuleRepository repository;

    private AlertRuleEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new AlertRuleEvaluationServiceImpl(
                repository,
                new ThresholdComparisonServiceImpl(),
                new RuleMatchingServiceImpl(),
                new EvaluationDuplicateGuard(100, Duration.ofMinutes(1), Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC)));
    }

    @Test
    void evaluatesProjectWideAndMatchingDeviceRulesInRepositoryOrder() {
        AlertRule projectRule = rule(UUID.randomUUID(), PROJECT_ID, null, null, AlertRuleComparisonOperator.GREATER_THAN, BigDecimal.TEN);
        AlertRule deviceRule = rule(RULE_ID, PROJECT_ID, "device-1", null, AlertRuleComparisonOperator.EQUAL, BigDecimal.TEN);
        AlertRule otherProjectRule = rule(UUID.randomUUID(), OTHER_PROJECT_ID, "device-1", null, AlertRuleComparisonOperator.EQUAL, BigDecimal.TEN);
        when(repository.findEnabledByProjectIdAndMetricType(PROJECT_ID, AlertRuleMetricType.CPU_USAGE))
                .thenReturn(List.of(projectRule, deviceRule, otherProjectRule));

        AlertEvaluationResponse response = service.evaluate(input("device-1", "sample-1", BigDecimal.TEN));

        assertThat(response.duplicate()).isFalse();
        assertThat(response.candidateCount()).isEqualTo(3);
        assertThat(response.evaluatedCount()).isEqualTo(2);
        assertThat(response.triggeredCount()).isEqualTo(1);
        assertThat(response.results()).extracting(AlertEvaluationResult::ruleId)
                .containsExactly(projectRule.getId(), deviceRule.getId());
    }

    @Test
    void excludesDisabledAndMetricMismatchBeforeEvaluation() {
        AlertRule candidate = rule(RULE_ID, PROJECT_ID, null, null, AlertRuleComparisonOperator.GREATER_THAN, BigDecimal.TEN);
        when(repository.findEnabledByProjectIdAndMetricType(PROJECT_ID, AlertRuleMetricType.CPU_USAGE))
                .thenReturn(List.of(candidate));

        AlertEvaluationResponse response = service.evaluate(input("device-1", "sample-2", BigDecimal.ONE));

        assertThat(response.evaluatedCount()).isEqualTo(1);
        assertThat(response.results().getFirst().triggered()).isFalse();
    }

    @Test
    void matchesServiceScopeAndRejectsDifferentService() {
        AlertRule rule = rule(RULE_ID, PROJECT_ID, null, SERVICE_ID, AlertRuleMetricType.RESPONSE_TIME_MS,
                AlertRuleComparisonOperator.LESS_THAN, BigDecimal.TEN);
        when(repository.findEnabledByProjectIdAndMetricType(PROJECT_ID, AlertRuleMetricType.RESPONSE_TIME_MS))
                .thenReturn(List.of(rule));

        AlertEvaluationResponse matching = service.evaluate(new AlertEvaluationInput(
                PROJECT_ID, AlertEvaluationSourceType.SERVICE, SERVICE_ID.toString(),
                AlertRuleMetricType.RESPONSE_TIME_MS, BigDecimal.ONE, Instant.now(), "sample-3"));
        AlertEvaluationResponse different = service.evaluate(new AlertEvaluationInput(
                PROJECT_ID, AlertEvaluationSourceType.SERVICE, UUID.randomUUID().toString(),
                AlertRuleMetricType.RESPONSE_TIME_MS, BigDecimal.ONE, Instant.now(), "sample-4"));

        assertThat(matching.evaluatedCount()).isEqualTo(1);
        assertThat(different.evaluatedCount()).isZero();
    }

    @Test
    void skipsInvalidRuleAndContinuesWithValidRule() {
        AlertRule invalid = mock(AlertRule.class);
        when(invalid.getId()).thenReturn(UUID.randomUUID());
        AlertRule valid = rule(RULE_ID, PROJECT_ID, null, null, AlertRuleComparisonOperator.GREATER_THAN, BigDecimal.ONE);
        when(repository.findEnabledByProjectIdAndMetricType(PROJECT_ID, AlertRuleMetricType.CPU_USAGE))
                .thenReturn(List.of(invalid, valid));

        AlertEvaluationResponse response = service.evaluate(input("device-1", "sample-5", BigDecimal.TEN));

        assertThat(response.candidateCount()).isEqualTo(2);
        assertThat(response.evaluatedCount()).isEqualTo(1);
        assertThat(response.results()).extracting(AlertEvaluationResult::ruleId).containsExactly(RULE_ID);
    }

    @Test
    void suppressesDuplicateInputButEvaluatesDifferentSample() {
        AlertRule candidate = rule(RULE_ID, PROJECT_ID, null, null, AlertRuleComparisonOperator.GREATER_THAN, BigDecimal.ONE);
        when(repository.findEnabledByProjectIdAndMetricType(PROJECT_ID, AlertRuleMetricType.CPU_USAGE))
                .thenReturn(List.of(candidate));

        AlertEvaluationResponse first = service.evaluate(input("device-1", "sample-6", BigDecimal.TEN));
        AlertEvaluationResponse duplicate = service.evaluate(input("device-1", "sample-6", BigDecimal.TEN));
        AlertEvaluationResponse different = service.evaluate(input("device-1", "sample-7", BigDecimal.TEN));

        assertThat(first.duplicate()).isFalse();
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(different.duplicate()).isFalse();
    }

    @Test
    void rejectsIncompleteAndMalformedInput() {
        assertThatThrownBy(() -> service.evaluate(null)).isInstanceOf(AlertEvaluationValidationException.class);
        assertThatThrownBy(() -> service.evaluate(new AlertEvaluationInput(
                PROJECT_ID, AlertEvaluationSourceType.SERVICE, "bad", AlertRuleMetricType.STATUS_CODE,
                BigDecimal.ONE, Instant.now(), "sample-8"))).isInstanceOf(AlertEvaluationValidationException.class);
    }

    @Test
    void evaluatesFiveHundredApplicableRulesUnderControlledTarget() {
        List<AlertRuleComparisonOperator> operators = List.of(AlertRuleComparisonOperator.values());
        List<AlertRule> candidates = IntStream.range(0, PERFORMANCE_RULE_COUNT)
                .mapToObj(index -> applicablePerformanceRule(index, operators.get(index % operators.size())))
                .toList();
        when(repository.findEnabledByProjectIdAndMetricType(PROJECT_ID, AlertRuleMetricType.CPU_USAGE))
                .thenReturn(candidates);

        assertCompletePerformanceEvaluation(service.evaluate(
                input("device-1", "performance-warm-up", new BigDecimal("100.00"))));

        List<Long> durationsMillis = new ArrayList<>();
        for (int iteration = 1; iteration <= PERFORMANCE_MEASURED_RUNS; iteration++) {
            long started = System.nanoTime();
            AlertEvaluationResponse response = service.evaluate(input(
                    "device-1", "performance-measured-" + iteration, new BigDecimal("100.00")));
            long durationNanos = System.nanoTime() - started;

            assertCompletePerformanceEvaluation(response);
            assertThat(durationNanos)
                    .as("controlled evaluation iteration %s must complete in under %s ms",
                            iteration, PERFORMANCE_TARGET_MILLIS)
                    .isLessThan(Duration.ofMillis(PERFORMANCE_TARGET_MILLIS).toNanos());
            durationsMillis.add(Duration.ofNanos(durationNanos).toMillis());
        }

        List<Long> sortedDurations = new ArrayList<>(durationsMillis);
        Collections.sort(sortedDurations);
        long minimum = sortedDurations.getFirst();
        long maximum = sortedDurations.getLast();
        double average = durationsMillis.stream().mapToLong(Long::longValue).average().orElseThrow();
        long median = sortedDurations.get(sortedDurations.size() / 2);
        System.out.printf(
                "SCRUM-702 controlled local measurement: rules=%d runs=%d durationsMs=%s minMs=%d maxMs=%d averageMs=%.2f medianMs=%d targetMs=<%d%n",
                PERFORMANCE_RULE_COUNT, PERFORMANCE_MEASURED_RUNS, durationsMillis,
                minimum, maximum, average, median, PERFORMANCE_TARGET_MILLIS);
    }

    private void assertCompletePerformanceEvaluation(AlertEvaluationResponse response) {
        assertThat(response.duplicate()).isFalse();
        assertThat(response.candidateCount()).isEqualTo(PERFORMANCE_RULE_COUNT);
        assertThat(response.evaluatedCount()).isEqualTo(PERFORMANCE_RULE_COUNT);
        assertThat(response.triggeredCount()).isEqualTo(PERFORMANCE_RULE_COUNT);
        assertThat(response.results()).hasSize(PERFORMANCE_RULE_COUNT).allMatch(AlertEvaluationResult::triggered);
    }

    private AlertRule applicablePerformanceRule(int index, AlertRuleComparisonOperator operator) {
        BigDecimal threshold = switch (operator) {
            case GREATER_THAN -> new BigDecimal("50.00");
            case GREATER_THAN_OR_EQUAL, LESS_THAN_OR_EQUAL, EQUAL -> new BigDecimal("100.00");
            case LESS_THAN -> new BigDecimal("150.00");
        };
        AlertRule candidate = rule(UUID.randomUUID(), PROJECT_ID,
                index % 2 == 0 ? null : "device-1", null, operator, threshold);
        org.mockito.Mockito.lenient().when(candidate.isEnabled()).thenReturn(true);
        return candidate;
    }

    private AlertEvaluationInput input(String sourceId, String sampleId, BigDecimal value) {
        return new AlertEvaluationInput(PROJECT_ID, AlertEvaluationSourceType.DEVICE, sourceId,
                AlertRuleMetricType.CPU_USAGE, value, Instant.parse("2026-08-10T00:00:00Z"), sampleId);
    }

    private AlertRule rule(UUID id, UUID projectId, String deviceId, UUID serviceId,
                           AlertRuleComparisonOperator operator, BigDecimal threshold) {
                return rule(id, projectId, deviceId, serviceId, AlertRuleMetricType.CPU_USAGE, operator, threshold);
        }

        private AlertRule rule(UUID id, UUID projectId, String deviceId, UUID serviceId,
                                                   AlertRuleMetricType metricType, AlertRuleComparisonOperator operator,
                                                   BigDecimal threshold) {
        AlertRule rule = mock(AlertRule.class);
        when(rule.getId()).thenReturn(id);
        when(rule.getProjectId()).thenReturn(projectId);
                when(rule.getMetricType()).thenReturn(metricType);
        when(rule.getThresholdValue()).thenReturn(threshold);
        when(rule.getComparisonOperator()).thenReturn(operator);
        when(rule.getSeverity()).thenReturn(Severity.HIGH);
        when(rule.getDeviceId()).thenReturn(deviceId);
        when(rule.getServiceId()).thenReturn(serviceId);
        return rule;
    }
}
