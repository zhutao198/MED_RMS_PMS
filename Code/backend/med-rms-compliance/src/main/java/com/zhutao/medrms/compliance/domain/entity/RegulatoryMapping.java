package com.zhutao.medrms.compliance.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("compliance_schema.t_regulatory_mapping")
public class RegulatoryMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String regulationType;

    private String clauseNumber;

    private String clauseTitle;

    private String requirement;

    private String complianceStatus;

    private String evidence;

    private String mappedRequirementIds;

    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}