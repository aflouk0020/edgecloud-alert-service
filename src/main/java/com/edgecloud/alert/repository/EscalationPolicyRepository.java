package com.edgecloud.alert.repository;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository; import com.edgecloud.alert.entity.EscalationPolicy;
public interface EscalationPolicyRepository extends JpaRepository<EscalationPolicy,UUID>{Optional<EscalationPolicy> findByProjectId(UUID projectId); Optional<EscalationPolicy> findByProjectIdAndEnabledTrue(UUID projectId);}
