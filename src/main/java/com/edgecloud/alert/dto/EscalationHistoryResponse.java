package com.edgecloud.alert.dto;
import java.time.Instant; import java.util.UUID; import com.edgecloud.alert.entity.*;
public record EscalationHistoryResponse(UUID id,UUID alertEventId,UUID policyId,int levelNumber,Severity previousSeverity,Severity resultingSeverity,String reason,Instant escalatedAt){public static EscalationHistoryResponse from(AlertEscalationHistory h){return new EscalationHistoryResponse(h.getId(),h.getAlertEventId(),h.getPolicyId(),h.getLevelNumber(),h.getPreviousSeverity(),h.getResultingSeverity(),h.getReason(),h.getEscalatedAt());}}
