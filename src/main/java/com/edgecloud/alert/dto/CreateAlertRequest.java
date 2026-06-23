
package com.edgecloud.alert.dto;

import com.edgecloud.alert.entity.AlertType;

import com.edgecloud.alert.entity.Severity;

import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.NotNull;

public record CreateAlertRequest(

        @NotNull

        AlertType alertType,

        @NotNull

        Severity severity,

        @NotBlank

        String message,

        @NotBlank

        String sourceService

) {

}

