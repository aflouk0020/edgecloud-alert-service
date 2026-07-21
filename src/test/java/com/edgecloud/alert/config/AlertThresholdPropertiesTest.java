package com.edgecloud.alert.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlertThresholdPropertiesTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsPositiveLatencyThreshold() {
        AlertThresholdProperties properties =
                new AlertThresholdProperties(1000);

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void rejectsZeroLatencyThreshold() {
        AlertThresholdProperties properties =
                new AlertThresholdProperties(0);

        assertThat(validator.validate(properties))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly(
                        "High latency threshold must be greater than zero"
                );
    }

    @Test
    void rejectsNegativeLatencyThreshold() {
        AlertThresholdProperties properties =
                new AlertThresholdProperties(-500);

        assertThat(validator.validate(properties))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly(
                        "High latency threshold must be greater than zero"
                );
    }
}
