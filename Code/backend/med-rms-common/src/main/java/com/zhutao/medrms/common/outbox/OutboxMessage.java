package com.zhutao.medrms.common.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v1.47 BUG #140 P0 修复：事务性发件箱（Transactional Outbox）实体
 * 业务事务内把事件写入 outbox 表，事务提交后由调度器异步发布到目标模块
 * 参考：Chris Richardson - Pattern: Transactional outbox
 */
@Data
@TableName("public.t_outbox_message")
public class OutboxMessage {

    @TableId(type = IdType.ASSIGN_UUID)
    private String eventId;

    private String eventType;
    private String aggregateType;
    private Long aggregateId;
    private String payload;       // JSON 字符串
    private String status;        // PENDING / PUBLISHED / FAILED
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private String lastError;
}
