package com.zhutao.medrms.risk.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("risk_schema.t_risk_matrix")
public class RiskMatrix {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String projectNo;

    private String matrixType; // FMEA/SFRA/HARA

    private String severity; // 1-5

    private String probability; // 1-5

    private String detectability; // 1-5

    private BigDecimal rpn; // Risk Priority Number = S x P x D

    private String riskLevel; // HIGH/MEDIUM/LOW

    private String riskZone; // RED/YELLOW/GREEN

    private String description;

    private String mitigationMeasure;

    private String residualRisk;

    private BigDecimal residualRpn;

    private LocalDateTime assessedAt;

    private Long assessedBy;

    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}