package com.edgecloud.alert.service;
import java.util.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import com.edgecloud.alert.dto.*; import com.edgecloud.alert.entity.*; import com.edgecloud.alert.exception.EscalationPolicyValidationException; import com.edgecloud.alert.repository.*;
@Service
public class EscalationPolicyService {
 private final EscalationPolicyRepository policies; private final AlertEscalationHistoryRepository history; private final AlertEventRepository alerts;
 public EscalationPolicyService(EscalationPolicyRepository p,AlertEscalationHistoryRepository h,AlertEventRepository a){policies=p;history=h;alerts=a;}
 @Transactional(readOnly=true) public Optional<EscalationPolicyResponse> get(UUID project){return policies.findByProjectId(project).map(EscalationPolicyResponse::from);}
 @Transactional public EscalationPolicyResponse save(UUID project,EscalationPolicyRequest request){validate(request); EscalationPolicy p=policies.findByProjectId(project).orElseGet(()->new EscalationPolicy(project,request.name(),request.enabled())); List<EscalationPolicyLevel> levels=request.levels().stream().map(l->new EscalationPolicyLevel(l.levelNumber(),l.elapsedSeconds(),l.targetSeverity(),l.enabled())).toList(); p.replace(request.name().trim(),request.enabled(),levels); return EscalationPolicyResponse.from(policies.save(p));}
 @Transactional public EscalationPolicyResponse setEnabled(UUID project,boolean enabled){EscalationPolicy p=policies.findByProjectId(project).orElseThrow(()->new EscalationPolicyValidationException("Escalation policy not found"));p.setEnabled(enabled);return EscalationPolicyResponse.from(p);}
 @Transactional(readOnly=true) public List<EscalationHistoryResponse> history(UUID project,UUID alert){if(alerts.findByIdAndProjectId(alert,project).isEmpty())throw new EscalationPolicyValidationException("Alert not found");return history.findByProjectIdAndAlertEventIdOrderByLevelNumber(project,alert).stream().map(EscalationHistoryResponse::from).toList();}
 private void validate(EscalationPolicyRequest r){Set<Integer> numbers=new HashSet<>();long previous=0;for(var l:r.levels().stream().sorted(Comparator.comparingInt(EscalationPolicyRequest.Level::levelNumber)).toList()){if(!numbers.add(l.levelNumber()))throw new EscalationPolicyValidationException("Escalation level numbers must be unique");if(l.elapsedSeconds()<=previous)throw new EscalationPolicyValidationException("Escalation thresholds must increase by level");previous=l.elapsedSeconds();}}
}
