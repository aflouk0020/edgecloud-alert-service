package com.edgecloud.alert.evaluation;

import java.time.Instant;
import java.util.List;

public record AlertEvaluationResponse(
        Instant evaluatedAt,
        boolean duplicate,
        int candidateCount,
        int evaluatedCount,
        int triggeredCount,
        List<AlertEvaluationResult> results) {
}