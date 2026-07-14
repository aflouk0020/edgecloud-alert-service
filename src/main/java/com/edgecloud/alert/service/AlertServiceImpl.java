package com.edgecloud.alert.service;

import com.edgecloud.alert.dto.AlertResponse;
import com.edgecloud.alert.dto.AlertSummaryResponse;
import com.edgecloud.alert.dto.CreateAlertRequest;
import com.edgecloud.alert.dto.RuleEvaluationRequest;
import com.edgecloud.alert.entity.AlertType;
import com.edgecloud.alert.entity.Severity;
import com.edgecloud.alert.entity.Alert;
import com.edgecloud.alert.exception.AlertNotFoundException;
import com.edgecloud.alert.repository.AlertRepository;
import com.edgecloud.alert.util.RootCauseSuggestionResolver;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AlertServiceImpl implements AlertService {

    private static final long HIGH_LATENCY_THRESHOLD_MS = 1000;

    private final AlertRepository alertRepository;

    public AlertServiceImpl(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Override
    public List<AlertResponse> getActiveAlerts() {
        return alertRepository.findByStatusOrderByCreatedAtDesc("ACTIVE")
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public AlertSummaryResponse getAlertSummary() {
        return new AlertSummaryResponse(
                alertRepository.count(),
                alertRepository.countByStatus("ACTIVE"),
                alertRepository.countByStatus("RESOLVED"),
                alertRepository.countBySeverity(Severity.LOW),
                alertRepository.countBySeverity(Severity.MEDIUM),
                alertRepository.countBySeverity(Severity.HIGH)
        );
    }

    @Override
    public List<AlertResponse> evaluateRules(
            RuleEvaluationRequest request) {

        List<AlertResponse> generated = new java.util.ArrayList<>();

        if ("DOWN".equalsIgnoreCase(request.serviceStatus())) {

            generated.add(
                    createIfNotExists(
                            AlertType.SERVICE_DOWN,
                            Severity.HIGH,
                            "Service is DOWN",
                            request.serviceName()
                    )
            );
        }

        if (request.responseTimeMs() != null &&
                request.responseTimeMs() > HIGH_LATENCY_THRESHOLD_MS) {

            generated.add(
                    createIfNotExists(
                            AlertType.HIGH_LATENCY,
                            Severity.MEDIUM,
                            "High response latency detected",
                            request.serviceName()
                    )
            );
        }

        if ("OFFLINE".equalsIgnoreCase(request.deviceStatus())) {

            generated.add(
                    createIfNotExists(
                            AlertType.DEVICE_OFFLINE,
                            Severity.HIGH,
                            "Device is OFFLINE",
                            request.deviceName()
                    )
            );
        }

        return generated;
    }

    @Override
    public AlertResponse createAlert(CreateAlertRequest request) {
        Alert alert = new Alert();
        alert.setAlertType(request.alertType());
        alert.setSeverity(request.severity());
        alert.setMessage(request.message());
        alert.setSourceService(request.sourceService());

        Alert saved = alertRepository.save(alert);

        return toResponse(saved);
    }

    @Override
    public AlertResponse resolveAlert(UUID id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new AlertNotFoundException(
                        "Alert not found: " + id
                ));

        alert.resolve();

        Alert saved = alertRepository.save(alert);

        return toResponse(saved);
    }

    private AlertResponse createIfNotExists(
            AlertType type,
            Severity severity,
            String message,
            String source) {

        var existing =
                alertRepository.findFirstByAlertTypeAndSourceServiceAndStatus(
                        type,
                        source,
                        "ACTIVE"
                );

        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Alert alert = new Alert();
        alert.setAlertType(type);
        alert.setSeverity(severity);
        alert.setMessage(message);
        alert.setSourceService(source);

        return toResponse(
                alertRepository.save(alert)
        );
    }

    private AlertResponse toResponse(Alert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getAlertType(),
                alert.getSeverity(),
                alert.getMessage(),
                alert.getSourceService(),
                alert.getStatus(),
                alert.isResolved(),
                alert.getResolvedAt(),
                alert.getCreatedAt(),
                RootCauseSuggestionResolver.resolve(alert.getAlertType())
        );
    }
}
