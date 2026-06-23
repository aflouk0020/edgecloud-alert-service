package com.edgecloud.alert.service;

import com.edgecloud.alert.dto.AlertResponse;
import com.edgecloud.alert.dto.CreateAlertRequest;

import java.util.List;
import java.util.UUID;

public interface AlertService {

    AlertResponse createAlert(CreateAlertRequest request);

    List<AlertResponse> getActiveAlerts();

    AlertResponse resolveAlert(UUID id);
}
