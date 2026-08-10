package com.edgecloud.alert.dto;

import jakarta.validation.constraints.NotNull;

public record AlertRuleEnabledRequest(@NotNull Boolean enabled) {
}