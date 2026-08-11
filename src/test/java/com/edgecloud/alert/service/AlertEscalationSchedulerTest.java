package com.edgecloud.alert.service;
import static org.mockito.Mockito.*; import java.util.*; import org.junit.jupiter.api.Test; import org.springframework.data.domain.Pageable; import com.edgecloud.alert.repository.AlertEventRepository;
class AlertEscalationSchedulerTest {
 @Test void evaluatesBoundedActiveBatchAndContinuesAfterEmptyResult(){AlertEventRepository alerts=mock(AlertEventRepository.class);AlertEscalationProcessor processor=mock(AlertEscalationProcessor.class);UUID first=UUID.randomUUID(),second=UUID.randomUUID();when(alerts.findActiveIds(any(Pageable.class))).thenReturn(List.of(first,second));AlertEscalationScheduler scheduler=new AlertEscalationScheduler(alerts,processor,25);scheduler.evaluate();verify(processor).process(eq(first),any());verify(processor).process(eq(second),any());}
}
