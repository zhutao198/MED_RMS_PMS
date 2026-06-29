package com.zhutao.medrms.requirement.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 需求主表 - 所有层级需求的公共字段
 * CTI (Class Table Inheritance) 模式
 */
@Data
@TableName("req_schema.t_requirement")
public class Requirement {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 需求编号，如 URS-P001-001 */
    private String requirementNo;

    /** 需求层级：URS/PRS/SRS/DRS */
    private String requirementType;

    /** 所属项目ID */
    private Long projectId;

    /** 需求标题 */
    private String title;

    /** 详细描述 */
    private String description;

    /** 优先级：MUST/SHOULD/COULD/WONT */
    private String priority;

    /** 状态 */
    private String status;

    /** 风险等级：HIGH/MEDIUM/LOW */
    private String riskLevel;

    /** 软件安全分类：A/B/C (IEC 62304) */
    private String safetyClass;

    /** 需求分类：SOFTWARE/HARDWARE/BOTH */
    private String requirementCategory;

    /** 需求来源：CUSTOMER/MARKET/REGULATION/INTERNAL/COMPETITOR（FR-0.6 / US-5） */
    private String source;

    /** 来源编号：原始需求编号/法规条款号 */
    private String sourceNo;

    /** 关联基线ID */
    private Long baselineId;

    /** 软删除标记 */
    @TableLogic
    private Boolean isDeleted;

    /** 变更影响 suspect 标记（FR-0.10） */
    private Boolean isSuspect;

    /** 创建人ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后修改人 */
    private Long updatedBy;

    /** 最后修改时间 */
    private LocalDateTime updatedAt;
}