package com.edgecloud.alert.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.edgecloud.alert.entity.AlertRuleComparisonOperator;

class ThresholdComparisonServiceImplTest {

    private final ThresholdComparisonService service = new ThresholdComparisonServiceImpl();

    @ParameterizedTest
    @MethodSource("comparisons")
    void comparesUsingBigDecimal(AlertRuleComparisonOperator operator, BigDecimal observed, boolean expected) {
        assertThat(service.compare(observed, BigDecimal.TEN, operator)).isEqualTo(expected);
    }

    private static Stream<Arguments> comparisons() {
        return Stream.of(
                Arguments.of(AlertRuleComparisonOperator.GREATER_THAN, new BigDecimal("10.01"), true),
                Arguments.of(AlertRuleComparisonOperator.GREATER_THAN_OR_EQUAL, BigDecimal.TEN, true),
                Arguments.of(AlertRuleComparisonOperator.LESS_THAN, new BigDecimal("9.99"), true),
                Arguments.of(AlertRuleComparisonOperator.LESS_THAN_OR_EQUAL, BigDecimal.TEN, true),
                Arguments.of(AlertRuleComparisonOperator.EQUAL, new BigDecimal("10.00"), true),
                Arguments.of(AlertRuleComparisonOperator.EQUAL, new BigDecimal("10.01"), false));
    }
}
