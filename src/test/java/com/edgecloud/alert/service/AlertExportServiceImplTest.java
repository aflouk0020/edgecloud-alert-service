package com.edgecloud.alert.service;

import com.edgecloud.alert.entity.Alert;
import com.edgecloud.alert.entity.AlertType;
import com.edgecloud.alert.entity.Severity;
import com.edgecloud.alert.repository.AlertRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class AlertExportServiceImplTest {


    @Test
    void exportsAlertsAsCsv() {

        AlertRepository repository = mock(AlertRepository.class);

        Alert alert = new Alert();

        alert.setAlertType(AlertType.SERVICE_DOWN);
        alert.setSeverity(Severity.HIGH);
        alert.setMessage("Service unavailable");
        alert.setSourceService("monitoring-service");

        alert.prePersist();


        when(repository.findAll())
                .thenReturn(List.of(alert));


        AlertExportService service =
                new AlertExportServiceImpl(repository);


        String csv = service.exportAlertsToCsv();


        assertThat(csv)
                .contains("alertType,severity,message")
                .contains("SERVICE_DOWN")
                .contains("HIGH")
                .contains("Service unavailable")
                .contains("monitoring-service");
    }
}
