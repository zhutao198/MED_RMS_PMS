package com.zhutao.medrms.compliance.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 统计快照 - CQRS Lite 读取模型
 * 详细设计: 支撑域与通用域-详细设计.md §3.2 StatisticsSnapshot
 * 写入走领域事件 + 定时计算；读取走快照表，避免实时聚合
 */
@Data
@TableName(value = "report_schema.statistics_snapshot", autoResultMap = true)
public class StatisticsSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 指标类型：REQUIREMENT / CHANGE / RISK / COMPLIANCE / TREND */
    private String metricType;

    /** 指标键：total / byStatus.Draft / coverage.forwardRate 等 */
    private String metricKey;

    private BigDecimal metricValue;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> dimensionJson;

    private LocalDateTime calculatedAt;
}
