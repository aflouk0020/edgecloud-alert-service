package com.edgecloud.alert.controller;
import java.util.*; import org.springframework.http.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import com.edgecloud.alert.dto.*; import com.edgecloud.alert.service.*; import jakarta.validation.Valid;
@RestController @RequestMapping("/api/v2/projects/{projectId}")
public class EscalationPolicyController {private final EscalationPolicyService service;private final AlertRuleAuthorizationService auth;public EscalationPolicyController(EscalationPolicyService s,AlertRuleAuthorizationService a){service=s;auth=a;}
 @GetMapping("/escalation-policy") public ResponseEntity<EscalationPolicyResponse> get(@PathVariable UUID projectId,Authentication a){auth.requireRead(projectId,a);return service.get(projectId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());}
 @PostMapping("/escalation-policy") public ResponseEntity<EscalationPolicyResponse> create(@PathVariable UUID projectId,@Valid @RequestBody EscalationPolicyRequest r,Authentication a){auth.requireAdminMutation(projectId,a);return ResponseEntity.status(HttpStatus.CREATED).body(service.save(projectId,r));}
 @PutMapping("/escalation-policy") public EscalationPolicyResponse update(@PathVariable UUID projectId,@Valid @RequestBody EscalationPolicyRequest r,Authentication a){auth.requireAdminMutation(projectId,a);return service.save(projectId,r);}
 @PatchMapping("/escalation-policy/enabled") public EscalationPolicyResponse enabled(@PathVariable UUID projectId,@Valid @RequestBody EscalationEnabledRequest r,Authentication a){auth.requireAdminMutation(projectId,a);return service.setEnabled(projectId,r.enabled());}
 @GetMapping("/alerts/{alertId}/escalations") public List<EscalationHistoryResponse> history(@PathVariable UUID projectId,@PathVariable UUID alertId,Authentication a){auth.requireRead(projectId,a);return service.history(projectId,alertId);}
}
