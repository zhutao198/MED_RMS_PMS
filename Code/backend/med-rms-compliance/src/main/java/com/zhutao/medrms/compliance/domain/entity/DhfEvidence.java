package com.zhutao.medrms.compliance.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("compliance_schema.t_dhf_evidence")
public class DhfEvidence {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String projectNo;

    private String evidenceType;

    private String evidenceName;

    private String filePath;

    private String fileHash;

    private String description;

    private String status;

    private Long createdBy;

    @TableField("uploaded_at")
    private LocalDateTime createdAt;

    private Boolean isDeleted = false;
}