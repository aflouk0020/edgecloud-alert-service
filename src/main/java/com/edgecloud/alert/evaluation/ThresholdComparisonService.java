package com.edgecloud.alert.evaluation;

import java.math.BigDecimal;

import com.edgecloud.alert.entity.AlertRuleComparisonOperator;

public interface ThresholdComparisonService {

    boolean compare(BigDecimal observedValue, BigDecimal threshold, AlertRuleComparisonOperator operator);
}