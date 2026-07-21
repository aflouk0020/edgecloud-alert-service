package com.edgecloud.alert.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "edgecloud.alert")
@Validated
public record AlertThresholdProperties(

        @Min(
                value = 1,
                message = "High latency threshold must be greater than zero"
        )
        long highLatencyThresholdMs

) {
}
