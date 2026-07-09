package com.zhutao.medrms.common.outbox;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventListener {

    private final OutboxService outboxService;

    @PostConstruct
    public void init() {
        outboxService.subscribe("ChangeApproved", event ->
                log.info("ChangeApproved event received for aggregateId={}", event.getAggregateId()));
        outboxService.subscribe("ChangeExecuted", event ->
                log.info("ChangeExecuted event received for aggregateId={}", event.getAggregateId()));
        outboxService.subscribe("TraceLinkCreated", event ->
                log.info("TraceLinkCreated event received for aggregateId={}", event.getAggregateId()));
        outboxService.subscribe("TraceGapIgnored", event ->
                log.info("TraceGapIgnored event received for aggregateId={}", event.getAggregateId()));
    }
}
