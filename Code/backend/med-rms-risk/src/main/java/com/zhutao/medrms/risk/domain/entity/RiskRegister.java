package com.zhutao.medrms.risk.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("risk_schema.t_risk_register")
public class RiskRegister {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String riskNo;

    private String riskTitle;

    private String category; // PRODUCT/PROCESS/SUPPLIER/REGULATORY

    private String severity; // CRITICAL/MAJOR/MINOR/NEGLIGIBLE

    private String probability; // HIGH/MEDIUM/LOW

    private String detectability; // EASY/MEDIUM/HARD

    private String riskLevel; // HIGH/MEDIUM/LOW

    private String description;

    private String rootCause;

    private String controlMeasure;

    private String responseStrategy; // AVOID/MITIGATE/TRANSFER/ACCEPT

    private String status; // OPEN/IN_PROGRESS/CLOSED/ACCEPTED

    private Long ownerId;

    private String ownerName;

    // R109 G3 修复：项目 ID（实现 RisksMatrix 按项目过滤）
    private Long projectId;

    // v1.45 BUG #95 修复：DB 列是 DATE 不可转 LocalDateTime，改 LocalDate
    private LocalDate dueDate;

    private LocalDateTime closedAt;

    private String closureNote;

    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}