package com.edgecloud.alert.service;

import com.edgecloud.alert.entity.Alert;
import com.edgecloud.alert.repository.AlertRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlertExportServiceImpl implements AlertExportService {

    private final AlertRepository alertRepository;

    public AlertExportServiceImpl(
            AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }


    @Override
    public String exportAlertsToCsv() {

        List<Alert> alerts = alertRepository.findAll();

        StringBuilder csv = new StringBuilder();

        csv.append(
                "id,alertType,severity,message,sourceService,status,resolved,resolvedAt,createdAt\n"
        );


        for (Alert alert : alerts) {

            csv.append(alert.getId()).append(",");
            csv.append(alert.getAlertType()).append(",");
            csv.append(alert.getSeverity()).append(",");
            csv.append(escape(alert.getMessage())).append(",");
            csv.append(alert.getSourceService()).append(",");
            csv.append(alert.getStatus()).append(",");
            csv.append(alert.isResolved()).append(",");
            csv.append(alert.getResolvedAt()).append(",");
            csv.append(alert.getCreatedAt());
            csv.append("\n");
        }

        return csv.toString();
    }


    private String escape(String value) {

        if (value == null) {
            return "";
        }

        return "\"" +
                value.replace("\"", "\"\"") +
                "\"";
    }
}
