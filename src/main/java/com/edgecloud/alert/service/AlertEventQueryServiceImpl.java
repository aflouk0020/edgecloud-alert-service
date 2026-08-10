package com.edgecloud.alert.service;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edgecloud.alert.dto.AlertEventFilter;
import com.edgecloud.alert.dto.AlertEventPageResponse;
import com.edgecloud.alert.dto.AlertEventResponse;
import com.edgecloud.alert.exception.AlertEventValidationException;
import com.edgecloud.alert.exception.AlertNotFoundException;
import com.edgecloud.alert.repository.AlertEventRepository;
import com.edgecloud.alert.repository.AlertEventSpecifications;

@Service
@Transactional(readOnly = true)
public class AlertEventQueryServiceImpl implements AlertEventQueryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAXIMUM_SIZE = 100;

    private final AlertEventRepository repository;

    public AlertEventQueryServiceImpl(AlertEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public AlertEventPageResponse list(UUID projectId, AlertEventFilter filter,
                                       Integer requestedPage, Integer requestedSize, String requestedDirection) {
        if (projectId == null) throw new AlertEventValidationException("projectId is required");
        validateFilter(filter);
        int page = requestedPage == null ? DEFAULT_PAGE : requestedPage;
        int size = requestedSize == null ? DEFAULT_SIZE : requestedSize;
        if (page < 0) throw new AlertEventValidationException("page must be zero or greater");
        if (size < 1 || size > MAXIMUM_SIZE) {
            throw new AlertEventValidationException("size must be between 1 and 100");
        }
        Sort.Direction direction = parseDirection(requestedDirection);
        var pageable = PageRequest.of(page, size,
                Sort.by(new Sort.Order(direction, "triggeredAt"), Sort.Order.asc("id")));
        var result = repository.findAll(AlertEventSpecifications.forProject(projectId, filter), pageable);
        return new AlertEventPageResponse(
                result.getContent().stream().map(AlertEventResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public AlertEventResponse get(UUID projectId, UUID alertId) {
        if (projectId == null || alertId == null) throw new AlertNotFoundException("Alert event not found");
        return repository.findByIdAndProjectId(alertId, projectId)
                .map(AlertEventResponse::from)
                .orElseThrow(() -> new AlertNotFoundException("Alert event not found"));
    }

    private void validateFilter(AlertEventFilter filter) {
        if (filter == null) return;
        if (filter.sourceId() != null && filter.sourceId().isBlank()) {
            throw new AlertEventValidationException("sourceId must not be blank");
        }
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new AlertEventValidationException("from must be before or equal to to");
        }
    }

    private Sort.Direction parseDirection(String requestedDirection) {
        if (requestedDirection == null) return Sort.Direction.DESC;
        if (requestedDirection.isBlank()) throw new AlertEventValidationException("sortDirection must be ASC or DESC");
        try {
            return Sort.Direction.fromString(requestedDirection);
        } catch (IllegalArgumentException ex) {
            throw new AlertEventValidationException("sortDirection must be ASC or DESC");
        }
    }
}
