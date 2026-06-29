package com.zhutao.medrms.change.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v1.47 BUG #110 P0 修复：单次审批记录（每次审批一条）
 * 取代 ChangeRequest 内嵌的 approvedBy/approvedAt/approvalComments（只能存一次）
 */
@Data
@TableName("chg_schema.t_change_approval")
public class ChangeApproval {

    public static final String DECISION_APPROVE = "APPROVE";
    public static final String DECISION_REJECT = "REJECT";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long changeId;

    private Long approverId;

    private String approverName;

    /** 决策：APPROVE / REJECT */
    private String decision;

    private String comments;

    /** 签名 ID（BUG #115 集成电子签名） */
    private Long signatureId;

    private LocalDateTime createdAt;
}
