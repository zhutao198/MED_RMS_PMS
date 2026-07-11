package com.zhutao.medrms.project.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 合规模板 - FR-1.9
 * 内置 NMPA / ISO 13485 / IEC 62304 / FDA 510(k) 4 个预设模板
 * 支持用户自定义模板
 */
@Data
@TableName("proj_schema.t_compliance_template")
public class ComplianceTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板编号：NMPA / ISO13485 / IEC62304 / FDA510K / CUSTOM_<id> */
    private String code;

    /** 模板名称（中文显示） */
    private String name;

    /** 类型：PRESET 系统预设 / CUSTOM 用户自定义 */
    private String type;

    /**
     * 模板分类：COMPLIANCE(合规) / REQUIREMENT(需求) / REVIEW(评审) / PROJECT(项目)
     * 默认 COMPLIANCE，R175 扩展支持多种模板类型（G7）
     */
    private String category;

    /** 模板说明 */
    private String description;

    /**
     * 模板配置 JSON 字符串，包含：
     * - defaultUrsFields: URS 预填字段（safety_class/regulatory_target/risk_class）
     * - reviewProcess: 评审流程节点（pre-review / formal-review / final-review）
     * - dcpGates: DCP 阶段门限（planning/requirements/design/verification/release）
     * - evidencePackage: 证据包模板字段
     * - regulationRefs: 法规引用（如 NMPA: "医疗器械注册管理办法"）
     */
    private String configJson;

    private Boolean isActive = true;

    private Long createdBy;

    private String createdByName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
