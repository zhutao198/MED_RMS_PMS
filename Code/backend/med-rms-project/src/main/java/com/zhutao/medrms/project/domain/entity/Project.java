package com.zhutao.medrms.project.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("proj_schema.t_project")
public class Project {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String projectNo;

    private String projectName;

    private String description;

    private String status; // PLANNING/IN_PROGRESS/COMPLETED/TERMINATED

    private Long managerId;

    private String managerName;

    /** 合规模板 ID（FR-1.9 创建项目时关联的模板） */
    private Long templateId;

    /** 合规模板编号（NMPA / ISO13485 / IEC62304 / FDA510K / CUSTOM） */
    private String templateCode;

    private LocalDate startDate;

    private LocalDate endDate;

    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}