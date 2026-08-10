package com.edgecloud.alert.repository;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.edgecloud.alert.dto.AlertEventFilter;
import com.edgecloud.alert.entity.AlertEvent;

public final class AlertEventSpecifications {

    private AlertEventSpecifications() {
    }

    public static Specification<AlertEvent> forProject(UUID projectId, AlertEventFilter filter) {
        return (root, query, builder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(builder.equal(root.get("projectId"), projectId));
            if (filter != null) {
                if (filter.status() != null) predicates.add(builder.equal(root.get("status"), filter.status()));
                if (filter.severity() != null) predicates.add(builder.equal(root.get("severity"), filter.severity()));
                if (filter.sourceType() != null) predicates.add(builder.equal(root.get("sourceType"), filter.sourceType()));
                if (filter.sourceId() != null && !filter.sourceId().isBlank()) {
                    predicates.add(builder.equal(root.get("sourceId"), filter.sourceId().trim()));
                }
                if (filter.ownerId() != null) predicates.add(builder.equal(root.get("ownerUserId"), filter.ownerId()));
                if (filter.from() != null) predicates.add(builder.greaterThanOrEqualTo(root.get("triggeredAt"), filter.from()));
                if (filter.to() != null) predicates.add(builder.lessThanOrEqualTo(root.get("triggeredAt"), filter.to()));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
