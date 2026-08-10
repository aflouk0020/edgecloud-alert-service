package com.edgecloud.alert.service;

import java.util.UUID;

import com.edgecloud.alert.dto.AlertEventFilter;
import com.edgecloud.alert.dto.AlertEventPageResponse;
import com.edgecloud.alert.dto.AlertEventResponse;

public interface AlertEventQueryService {

    AlertEventPageResponse list(UUID projectId, AlertEventFilter filter,
                                Integer page, Integer size, String sortDirection);

    AlertEventResponse get(UUID projectId, UUID alertId);
}
