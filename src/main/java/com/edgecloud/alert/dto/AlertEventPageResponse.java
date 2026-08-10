package com.edgecloud.alert.dto;

import java.util.List;

public record AlertEventPageResponse(
        List<AlertEventResponse> alerts,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
