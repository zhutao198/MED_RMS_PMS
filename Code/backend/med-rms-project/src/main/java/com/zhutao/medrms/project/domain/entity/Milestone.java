package com.zhutao.medrms.project.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("proj_schema.t_milestone")
public class Milestone {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String milestoneNo;

    private String name;

    private String description;

    private Long projectId;

    private String gateType; // DCP1/DCP2/DCP3/DCP4/DCP5

    private LocalDate plannedDate;

    private LocalDate actualDate;

    private String status; // PLANNED/IN_PROGRESS/COMPLETED/DELAYED

    private String checkResult; // PASS/FAIL/PENDING

    private String checkComments;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}