package com.zhutao.medrms.compliance.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘配置 - 用户布局持久化（CQRS 写模型）
 * 详细设计: 支撑域与通用域-详细设计.md §3.2 DashboardConfig
 */
@Data
@TableName(value = "report_schema.dashboard_config", autoResultMap = true)
public class DashboardConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> layoutJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> widgetsJson;

    @TableField("is_default")
    private Boolean isDefault = false;

    private OffsetDateTime updatedAt;

    private OffsetDateTime createdAt;
}
