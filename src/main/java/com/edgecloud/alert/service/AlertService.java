package com.edgecloud.alert.service;

import com.edgecloud.alert.dto.AlertResponse;
import com.edgecloud.alert.dto.CreateAlertRequest;

public interface AlertService {

    AlertResponse createAlert(CreateAlertRequest request);
}
