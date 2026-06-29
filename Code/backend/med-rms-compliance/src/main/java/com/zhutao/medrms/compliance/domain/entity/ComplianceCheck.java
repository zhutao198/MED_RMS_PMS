package com.zhutao.medrms.compliance.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("compliance_schema.t_compliance_check")
public class ComplianceCheck {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long requirementId;

    private String requirementNo;

    private String regulationType;

    private String checkItem;

    private String checkResult;

    private String status;

    private String remarks;

    private Long checkedBy;

    private String checkerName;

    private LocalDateTime checkedAt;

    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}