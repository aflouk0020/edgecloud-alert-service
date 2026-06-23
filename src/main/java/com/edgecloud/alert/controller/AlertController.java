package com.edgecloud.alert.controller;

import com.edgecloud.alert.dto.AlertResponse;
import com.edgecloud.alert.dto.CreateAlertRequest;
import com.edgecloud.alert.service.AlertService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlertResponse createAlert(
            @Valid @RequestBody CreateAlertRequest request) {

        return alertService.createAlert(request);
    }
}
