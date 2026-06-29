package com.zhutao.medrms.compliance.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志表 - 追加只写，符合 21 CFR Part 11
 * 使用哈希链保证完整性
 */
@Data
@TableName("compliance_schema.t_audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 前一条记录的SHA-256哈希值 */
    private String prevHash;

    /** 本条记录的SHA-256哈希值 */
    private String currentHash;

    /** 事件类型：CREATE/MODIFY/DELETE/STATUS_CHANGE/SIGN/LOGIN */
    private String eventType;

    /** 实体类型 */
    private String entityType;

    /** 实体ID */
    private Long entityId;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作描述 */
    private String operation;

    /** 变更前值 JSON 字符串（oldValueJson ↔ old_value） */
    private String oldValue;

    /** 变更后值 JSON 字符串（newValueJson ↔ new_value） */
    private String newValue;

    /** 操作原因 */
    private String reason;

    /** 操作IP地址 */
    private String ipAddress;

    /** 日志创建时间 */
    private LocalDateTime createdAt;
}