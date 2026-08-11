package com.edgecloud.alert.repository;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository; import com.edgecloud.alert.entity.AlertEscalationHistory;
public interface AlertEscalationHistoryRepository extends JpaRepository<AlertEscalationHistory,UUID>{boolean existsByAlertEventIdAndLevelNumber(UUID alertId,int level); List<AlertEscalationHistory> findByProjectIdAndAlertEventIdOrderByLevelNumber(UUID projectId,UUID alertId);}
