package com.zhutao.medrms.esignature.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v1.46 BUG #104 修复：签名意图（21 CFR Part 11 §11.200 访问控制时效性）
 * 签名流程必须先建 Intent（未过期 + requesterId 匹配），再签名消费 Intent。
 */
@Data
@TableName("esign_schema.t_signature_intent")
public class SignatureIntent {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONSUMED = "CONSUMED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 意向编号 */
    @TableField("intent_no")
    private String intentNo;

    /** 申请人用户ID（谁发起的签名） */
    @TableField(value = "requester_id")
    private Long requesterId;

    /** 文档类型 */
    @TableField("document_type")
    private String documentType;

    /** 文档ID */
    @TableField("document_id")
    private Long documentId;

    /** 意向代码：APPROVE/REJECT/REVIEW/CONFIRM/RELEASE */
    @TableField("intent_code")
    private String intentCode;

    /** 含义代码：与 intentCode 类似但语义更明确 */
    @TableField("meaning_code")
    private String meaningCode;

    /** 状态：PENDING/CONSUMED/EXPIRED/CANCELLED */
    private String status;

    /** 过期时间（默认 15 分钟） */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /** 消费时间（实际签名时间） */
    @TableField("consumed_at")
    private LocalDateTime consumedAt;

    /** 实际签名人 */
    @TableField("consumed_by")
    private Long consumedBy;

    /** 关联签名记录 ID */
    @TableField("signature_id")
    private Long signatureId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
