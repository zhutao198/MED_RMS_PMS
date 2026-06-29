package com.zhutao.medrms.change.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("chg_schema.t_impact_assessment")
public class ImpactAssessment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("change_request_id")
    private Long changeId;

    private String itemNo;

    private String itemName;

    private String itemType;

    private String impactLevel;

    private String impactType;

    private String impactDescription;

    private String suggestedAction;

    @TableField(exist = false)
    private BigDecimal impactRatio;

    @TableField(exist = false)
    private LocalDateTime assessedAt;

    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}