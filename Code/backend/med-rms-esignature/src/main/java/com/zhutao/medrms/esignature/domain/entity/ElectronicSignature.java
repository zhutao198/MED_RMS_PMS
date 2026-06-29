package com.zhutao.medrms.esignature.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 电子签名记录表
 * 符合 21 CFR Part 11 要求
 */
@Data
@TableName("esign_schema.t_signature_record")
public class ElectronicSignature {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 签名类型：APPROVE/REJECT/REVIEW/CONFIRM */
    private String signatureType;

    /** 签名意图 */
    private String intent;

    /** 签名者用户ID */
    private Long signerId;

    /** 签名者姓名 */
    private String signerName;

    /** 签名者角色 */
    private String signerRole;

    /** 文档类型 */
    private String documentType;

    /** 文档ID */
    private Long documentId;

    /** 文档编号 */
    private String documentNo;

    /** 签名哈希值（兼容旧字段，写入时与 signatureValue 相同） */
    private String signatureHash;

    /**
     * 21 CFR Part 11 §11.10(e) 实体哈希：被签文档的唯一指纹
     * 用于 verify 时重算比对，防止文档被篡改
     */
    private String entityHash;

    /**
     * 签名值 = SHA-256(entityType + entityId + entityHash + meaningCode + signerId + timestamp)
     * 21 CFR Part 11 §11.70 防篡改硬约束
     */
    private String signatureValue;

    /** 签名方法 */
    private String signatureMethod;

    /** 签名IP地址 */
    private String ipAddress;

    /** 设备信息 */
    private String deviceInfo;

    /** 签名是否有效 */
    private Boolean isValid = true;

    /** 签名原因 */
    private String reason;

    /** 签名时间 */
    private LocalDateTime signedAt;
}