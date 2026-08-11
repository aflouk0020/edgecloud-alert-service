package com.edgecloud.alert.service;
import java.time.*; import org.slf4j.*; import org.springframework.beans.factory.annotation.Value; import org.springframework.data.domain.PageRequest; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component; import com.edgecloud.alert.repository.AlertEventRepository;
@Component
public class AlertEscalationScheduler {private static final Logger log=LoggerFactory.getLogger(AlertEscalationScheduler.class);private final AlertEventRepository alerts;private final AlertEscalationProcessor processor;private final int batch;
 public AlertEscalationScheduler(AlertEventRepository a,AlertEscalationProcessor p,@Value("${alert.escalation.batch-size:100}") int b){alerts=a;processor=p;batch=b;}
 @Scheduled(fixedDelayString="${alert.escalation.poll-interval-ms:30000}") public void evaluate(){int count=0;for(var id:alerts.findActiveIds(PageRequest.of(0,batch)))count+=processor.process(id,Instant.now());if(count>0)log.info("Processed {} alert escalation levels",count);}
}
