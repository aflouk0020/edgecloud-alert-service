package com.edgecloud.alert.controller;

import com.edgecloud.alert.dto.AlertResponse;
import com.edgecloud.alert.dto.CreateAlertRequest;
import com.edgecloud.alert.service.AlertService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public List<AlertResponse> getActiveAlerts() {
        return alertService.getActiveAlerts();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlertResponse createAlert(
            @Valid @RequestBody CreateAlertRequest request) {

        return alertService.createAlert(request);
    }

    @PutMapping("/{id}/resolve")
    public AlertResponse resolveAlert(@PathVariable UUID id) {
        return alertService.resolveAlert(id);
    }
}
