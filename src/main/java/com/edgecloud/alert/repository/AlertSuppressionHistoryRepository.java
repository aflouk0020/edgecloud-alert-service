package com.edgecloud.alert.repository;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository; import com.edgecloud.alert.entity.AlertSuppressionHistory;
public interface AlertSuppressionHistoryRepository extends JpaRepository<AlertSuppressionHistory,UUID>{boolean existsByMaintenanceWindowIdAndDedupKey(UUID windowId,String key);List<AlertSuppressionHistory> findByProjectIdAndMaintenanceWindowIdOrderBySuppressedAtDesc(UUID projectId,UUID windowId);long countByMaintenanceWindowId(UUID windowId);}
