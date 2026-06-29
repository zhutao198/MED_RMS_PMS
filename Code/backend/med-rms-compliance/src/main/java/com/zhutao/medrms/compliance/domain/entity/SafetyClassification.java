package com.zhutao.medrms.compliance.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v1.47 BUG #119 P0 修复：软件安全分类（IEC 62304 §5）
 * CLASS_A: 无伤害（Class I 医疗器械）
 * CLASS_B: 非严重伤害（Class II）
 * CLASS_C: 死亡或严重伤害（Class III）
 */
@Data
@TableName("compliance_schema.t_safety_classification")
public class SafetyClassification {

    public static final String CLASS_A = "CLASS_A";
    public static final String CLASS_B = "CLASS_B";
    public static final String CLASS_C = "CLASS_C";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 分类结果：CLASS_A / CLASS_B / CLASS_C */
    private String safetyClass;

    /** 分类依据（JSON 字符串：判定问题列表 + 答案） */
    private String classificationRationale;

    /** 分类人 userId */
    private Long classifiedBy;

    /** 复核人 userId（双签锁定 BUG #124） */
    private Long reviewedBy;

    /** 复核时间 */
    private LocalDateTime reviewedAt;

    /** 状态：DRAFT / UNDER_REVIEW / LOCKED */
    private String status;

    private String remarks;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
