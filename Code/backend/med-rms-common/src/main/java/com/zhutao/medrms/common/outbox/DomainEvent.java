package com.zhutao.medrms.common.outbox;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * v1.47 BUG #140 P0 修复：领域事件基础对象
 * 跨模块协作使用 Outbox 模式：业务事务内写 event_outbox，异步发布
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DomainEvent {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_FAILED = "FAILED";

    private String eventId;
    private String eventType;     // 如 "RequirementReviewed", "ChangeApproved"
    private String aggregateType; // 如 "requirement", "change"
    private Long aggregateId;
    private Map<String, Object> payload;
    private String status;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private String lastError;

    public static DomainEvent create(String eventType, String aggregateType, Long aggregateId, Map<String, Object> payload) {
        DomainEvent e = new DomainEvent();
        e.setEventId(UUID.randomUUID().toString());
        e.setEventType(eventType);
        e.setAggregateType(aggregateType);
        e.setAggregateId(aggregateId);
        e.setPayload(payload == null ? new HashMap<>() : payload);
        e.setStatus(STATUS_PENDING);
        e.setRetryCount(0);
        e.setCreatedAt(LocalDateTime.now());
        return e;
    }
}
