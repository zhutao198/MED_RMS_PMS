package com.zhutao.medrms.traceability.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v1.55 修复：追溯缺口忽略记录
 * 用于 TraceGaps.vue "忽略" 操作的去重持久化
 * key = (projectId, gapKey) — gapKey 是 type+requirementId 的拼接
 */
@Data
@TableName("trace_schema.t_trace_gap_ignored")
public class TraceGapIgnored {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目 ID */
    private Long projectId;

    /** 缺口类型：MISSING_CHILDREN/ORPHAN/NO_TEST_CASE */
    private String gapType;

    /** 缺口关联需求 ID */
    private Long requirementId;

    /** 忽略原因（可选） */
    private String reason;

    /** 忽略人 ID */
    private Long ignoredBy;

    /** 忽略时间 */
    private LocalDateTime ignoredAt;
}
