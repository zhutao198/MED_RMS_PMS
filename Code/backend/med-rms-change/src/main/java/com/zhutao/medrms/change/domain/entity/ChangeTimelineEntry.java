package com.zhutao.medrms.change.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v1.47 P0 修复：变更时间线（BUG #142）
 * 设计依据：chg-mgr-详细设计.md §2 TimelineEntry
 * 记录变更的完整生命周期事件（创建/提交/分析/审批/执行/完成/取消）
 * 关键时间点不依赖外部系统，可独立查询
 */
@Data
@TableName("chg_schema.t_change_timeline")
public class ChangeTimelineEntry {

    public static final String EVENT_CREATED = "CREATED";
    public static final String EVENT_SUBMITTED = "SUBMITTED";
    public static final String EVENT_IMPACT_ASSESSED = "IMPACT_ASSESSED";
    public static final String EVENT_APPROVED = "APPROVED";
    public static final String EVENT_REJECTED = "REJECTED";
    public static final String EVENT_EXECUTED = "EXECUTED";
    public static final String EVENT_EMERGENCY_EXECUTED = "EMERGENCY_EXECUTED";
    public static final String EVENT_VERIFIED = "VERIFIED";
    public static final String EVENT_CLOSED = "CLOSED";
    public static final String EVENT_CANCELLED = "CANCELLED";
    public static final String EVENT_SIGNED = "SIGNED";
    public static final String EVENT_OA_DISPATCHED = "OA_DISPATCHED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long changeId;

    private String event;

    private Long operatorId;

    private String operatorName;

    /** 详细信息（JSON 字符串） */
    private String details;

    /** 关联签名 ID（如有） */
    private Long signatureId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
