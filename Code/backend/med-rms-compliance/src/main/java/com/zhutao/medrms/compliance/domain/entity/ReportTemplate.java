package com.zhutao.medrms.compliance.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 报表模板 - 模板元数据 + 配置
 * 详细设计: 支撑域与通用域-详细设计.md §3.2 ReportTemplate
 */
@Data
@TableName(value = "report_schema.report_template", autoResultMap = true)
public class ReportTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String type;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> templateConfigJson;

    private String description;

    @TableField("is_active")
    private Boolean isActive = true;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
