package com.edgecloud.alert.dto;
import java.time.Instant; import java.util.*; import com.edgecloud.alert.entity.*;
public record EscalationPolicyResponse(UUID id,UUID projectId,String name,boolean enabled,List<Level> levels,Instant createdAt,Instant updatedAt){
 public record Level(UUID id,int levelNumber,long elapsedSeconds,Severity targetSeverity,boolean enabled){}
 public static EscalationPolicyResponse from(EscalationPolicy p){return new EscalationPolicyResponse(p.getId(),p.getProjectId(),p.getName(),p.isEnabled(),p.getLevels().stream().map(l->new Level(l.getId(),l.getLevelNumber(),l.getElapsedSeconds(),l.getTargetSeverity(),l.isEnabled())).toList(),p.getCreatedAt(),p.getUpdatedAt());}
}
