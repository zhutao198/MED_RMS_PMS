package com.zhutao.medrms.compliance.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v1.47 BUG #121 P0 修复：PR 纠正措施（ISO 13485 §8.5.2 CAPA 子表）
 * 每条 PrCorrection 关联一个 ProblemReport
 */
@Data
@TableName("compliance_schema.t_pr_correction")
public class PrCorrection {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_CLOSED = "CLOSED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联问题报告 ID */
    private Long problemReportId;

    /** 纠正措施描述 */
    private String action;

    /** 责任人 userId */
    private Long ownerId;

    /** 计划完成日期 */
    private LocalDateTime dueDate;

    /** 实际完成日期 */
    private LocalDateTime completedAt;

    /** 验证人 userId（双人签名锁定 BUG #124） */
    private Long verifiedBy;

    /** 验证时间 */
    private LocalDateTime verifiedAt;

    /** 状态：OPEN / IN_PROGRESS / VERIFIED / CLOSED */
    private String status;

    /** 效果验证说明 */
    private String effectiveness;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
