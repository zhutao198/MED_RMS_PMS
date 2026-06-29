package com.zhutao.medrms.compliance.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自定义报表配置（v1.46 P1-后端-1）
 * 持久化 ReportsCustom.vue 用户保存的字段/筛选/项目配置。
 */
@Data
@TableName("compliance_schema.t_report_config")
public class ReportConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String reportType;

    private Long projectId;

    private String fieldsJson;

    private String filtersJson;

    private Long createdBy;

    private String createdByName;

    private Boolean isShared;

    @TableLogic
    private Boolean isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
