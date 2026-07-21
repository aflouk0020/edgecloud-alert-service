package com.edgecloud.alert.controller;

import com.edgecloud.alert.service.AlertExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/export")
public class AlertExportController {

    private final AlertExportService alertExportService;


    public AlertExportController(
            AlertExportService alertExportService) {
        this.alertExportService = alertExportService;
    }


    @GetMapping(
            value = "/alerts/csv",
            produces = "text/csv"
    )
    public ResponseEntity<String> exportAlerts() {

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=alerts.csv"
                )
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(alertExportService.exportAlertsToCsv());
    }
}
