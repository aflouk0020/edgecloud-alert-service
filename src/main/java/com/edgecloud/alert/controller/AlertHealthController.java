package com.edgecloud.alert.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AlertHealthController {

    @GetMapping("/alerts/status")
    public String status() {
        return "Alert Service is running";
    }
}
