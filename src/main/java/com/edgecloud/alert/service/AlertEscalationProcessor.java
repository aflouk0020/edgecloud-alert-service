package com.edgecloud.alert.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.edgecloud.alert.entity.*;
import com.edgecloud.alert.repository.*;

@Service
public class AlertEscalationProcessor {
    private final AlertEventRepository alerts;
    private final EscalationPolicyRepository policies;
    private final AlertEscalationHistoryRepository history;
    private final AlertNotificationOutboxService outbox;
    public AlertEscalationProcessor(AlertEventRepository alerts, EscalationPolicyRepository policies,
            AlertEscalationHistoryRepository history, AlertNotificationOutboxService outbox) {
        this.alerts=alerts; this.policies=policies; this.history=history; this.outbox=outbox;
    }
    @Transactional
    public int process(UUID alertId, Instant now) {
        AlertEvent alert=alerts.findByIdForEscalation(alertId).orElse(null);
        if(alert==null || alert.getStatus()==AlertEventStatus.RESOLVED) return 0;
        EscalationPolicy policy=policies.findByProjectIdAndEnabledTrue(alert.getProjectId()).orElse(null);
        if(policy==null) return 0;
        long elapsed=Duration.between(alert.getTriggeredAt(),now).getSeconds();
        int processed=0;
        for(EscalationPolicyLevel level:policy.getLevels()) {
            if(!level.isEnabled() || level.getElapsedSeconds()>elapsed || history.existsByAlertEventIdAndLevelNumber(alertId,level.getLevelNumber())) continue;
            Severity before=alert.getSeverity();
            String reason=alert.getStatus()==AlertEventStatus.OPEN ? "UNACKNOWLEDGED" : "UNRESOLVED";
            alert.escalate(level.getLevelNumber(),level.getTargetSeverity(),now);
            history.save(new AlertEscalationHistory(alert,policy.getId(),level.getLevelNumber(),before,alert.getSeverity(),reason,now));
            outbox.enqueueEscalation(alert,level.getLevelNumber(),reason,now.plusNanos(level.getLevelNumber()*1000L));
            processed++;
        }
        return processed;
    }
}
