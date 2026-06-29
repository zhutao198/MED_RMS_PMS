package com.zhutao.medrms.compliance.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("compliance_schema.t_soup_component")
public class SoupComponent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String componentName;

    private String componentCode;

    private String version;

    private String supplier;

    private String supplierCountry;

    private String softwareCategory;

    private String complianceStandard;

    private String usageScenario;

    private String integrationLevel;

    private String riskLevel;

    private String certificationDoc;

    private String licenseType;

    private LocalDateTime licenseExpiry;

    private String status;

    private String securityDisclosure;

    private String maintainedBy;

    @TableField(exist = false)
    private Long projectId;

    private LocalDateTime lastSecurityUpdate;

    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}