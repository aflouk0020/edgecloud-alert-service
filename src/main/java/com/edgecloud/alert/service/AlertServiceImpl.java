package com.edgecloud.alert.service;

import com.edgecloud.alert.dto.AlertResponse;
import com.edgecloud.alert.dto.CreateAlertRequest;
import com.edgecloud.alert.entity.Alert;
import com.edgecloud.alert.exception.AlertNotFoundException;
import com.edgecloud.alert.repository.AlertRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AlertServiceImpl implements AlertService {

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
                alert.getCreatedAt()
        );
    }
}
