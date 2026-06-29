package com.zhutao.medrms.common.outbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhutao.medrms.common.outbox.mapper.OutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * v1.47 BUG #140 P0 修复：Outbox 服务（事务性发件箱 + 异步发布）
 * 业务模块调用 outbox.append(event) 在事务内落库；定时器拉取 PENDING 状态的事件并通过
 * in-process listener（无 Kafka 部署）同步调用订阅者。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** v1.47 in-process 订阅者表（key = eventType） */
    private static final Map<String, List<Consumer<DomainEvent>>> subscribers = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 业务事务内追加事件（必须与业务写入同事务）
     */
    @Transactional
    public void append(String eventType, String aggregateType, Long aggregateId, Map<String, Object> payload) {
        DomainEvent e = DomainEvent.create(eventType, aggregateType, aggregateId, payload);
        OutboxMessage m = new OutboxMessage();
        m.setEventId(e.getEventId());
        m.setEventType(e.getEventType());
        m.setAggregateType(e.getAggregateType());
        m.setAggregateId(e.getAggregateId());
        try {
            m.setPayload(objectMapper.writeValueAsString(e.getPayload()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Outbox payload serialize failed", ex);
        }
        m.setStatus(DomainEvent.STATUS_PENDING);
        m.setRetryCount(0);
        m.setCreatedAt(e.getCreatedAt());
        outboxMapper.insert(m);
        log.debug("Outbox append: eventId={}, type={}, aggregate={}/{}",
                e.getEventId(), e.getEventType(), e.getAggregateType(), e.getAggregateId());
    }

    /**
     * v1.47 in-process 订阅：模块启动时调用 outboxService.subscribe("xxx", event -> ...)
     */
    public void subscribe(String eventType, Consumer<DomainEvent> handler) {
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /**
     * 定时发布：每 30 秒拉取 PENDING 事件（最多 100 条），逐个分发给 in-process 订阅者。
     * 失败重试 3 次后置 FAILED。
     */
    @Scheduled(fixedDelay = 30_000L, initialDelay = 10_000L)
    @Transactional
    public void publishPending() {
        LambdaQueryWrapper<OutboxMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OutboxMessage::getStatus, DomainEvent.STATUS_PENDING)
                .lt(OutboxMessage::getRetryCount, 3)
                .orderByAsc(OutboxMessage::getCreatedAt)
                .last("LIMIT 100");
        List<OutboxMessage> pending = outboxMapper.selectList(wrapper);
        if (pending.isEmpty()) return;

        for (OutboxMessage m : pending) {
            try {
                DomainEvent e = toDomainEvent(m);
                List<Consumer<DomainEvent>> handlers = subscribers.getOrDefault(m.getEventType(), List.of());
                for (Consumer<DomainEvent> h : handlers) {
                    try {
                        h.accept(e);
                    } catch (Exception subEx) {
                        log.warn("Outbox subscriber error: eventType={}, err={}", m.getEventType(), subEx.getMessage());
                    }
                }
                m.setStatus(DomainEvent.STATUS_PUBLISHED);
                m.setPublishedAt(LocalDateTime.now());
                m.setRetryCount((m.getRetryCount() == null ? 0 : m.getRetryCount()) + 1);
                outboxMapper.updateById(m);
            } catch (Exception ex) {
                m.setRetryCount((m.getRetryCount() == null ? 0 : m.getRetryCount()) + 1);
                m.setLastError(ex.getMessage());
                if (m.getRetryCount() >= 3) {
                    m.setStatus(DomainEvent.STATUS_FAILED);
                }
                outboxMapper.updateById(m);
                log.error("Outbox publish failed: eventId={}, type={}, retry={}",
                        m.getEventId(), m.getEventType(), m.getRetryCount(), ex);
            }
        }
    }

    private DomainEvent toDomainEvent(OutboxMessage m) {
        DomainEvent e = new DomainEvent();
        e.setEventId(m.getEventId());
        e.setEventType(m.getEventType());
        e.setAggregateType(m.getAggregateType());
        e.setAggregateId(m.getAggregateId());
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(m.getPayload(), Map.class);
            e.setPayload(payload);
        } catch (Exception ignore) {
            e.setPayload(Map.of());
        }
        e.setStatus(m.getStatus());
        e.setRetryCount(m.getRetryCount());
        e.setCreatedAt(m.getCreatedAt());
        e.setPublishedAt(m.getPublishedAt());
        e.setLastError(m.getLastError());
        return e;
    }
}
