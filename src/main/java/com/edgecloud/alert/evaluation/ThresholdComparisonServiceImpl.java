package com.edgecloud.alert.evaluation;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.edgecloud.alert.entity.AlertRuleComparisonOperator;

@Service
public class ThresholdComparisonServiceImpl implements ThresholdComparisonService {

    @Override
    public boolean compare(BigDecimal observedValue, BigDecimal threshold, AlertRuleComparisonOperator operator) {
        int comparison = observedValue.compareTo(threshold);
        return switch (operator) {
            case GREATER_THAN -> comparison > 0;
            case GREATER_THAN_OR_EQUAL -> comparison >= 0;
            case LESS_THAN -> comparison < 0;
            case LESS_THAN_OR_EQUAL -> comparison <= 0;
            case EQUAL -> comparison == 0;
        };
    }
}