package com.zhutao.medrms.risk.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("risk_schema.t_risk_assessment")
public class RiskAssessment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long requirementId;

    private String riskLevel; // HIGH/MEDIUM/LOW

    private String hazardLevel; // CATASTROPHIC/CRITICAL/MAJOR/MINOR/NEGLIGIBLE

    private BigDecimal riskScore;

    private String hazardSource;

    private String hazardSituation;

    private String harm;

    private String controlMeasure;

    private String residualRisk; // ACCEPTABLE/UNACCEPTABLE/ALARP

    private String riskStatus; // OPEN/CLOSED/MONITORING

    private Long assessedBy;

    private LocalDateTime assessedAt;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    // FMEA 字段 (FR-1.8)
    private Integer severity;   // S 1-10
    private Integer occurrence; // O 1-10
    private Integer detection;  // D 1-10
    private Integer rpn;        // S*O*D
    private String actionPlan;
    private String actionOwner;
    private LocalDateTime actionDueDate;
    private String actionStatus; // OPEN/IN_PROGRESS/COMPLETED

    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}