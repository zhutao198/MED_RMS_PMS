package com.zhutao.medrms.compliance.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("compliance_schema.t_problem_report")
public class ProblemReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String reportCode;

    private Long projectId;

    private String projectName;

    private String title;

    private String severity;

    private String description;

    private String status;

    private LocalDateTime discoveryDate;

    private String sourceType;

    private String affectedItems;

    private Long reporterId;

    private String reporterName;

    private Long assigneeId;

    private String assigneeName;

    private LocalDateTime resolvedAt;

    private String resolution;

    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}