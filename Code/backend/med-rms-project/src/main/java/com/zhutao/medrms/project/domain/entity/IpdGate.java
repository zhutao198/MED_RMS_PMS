package com.zhutao.medrms.project.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("proj_schema.t_ipd_gate")
public class IpdGate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String projectNo;

    private Integer gateNo;

    private String gateName;

    private String gateType; // PLANNING/DEFINE/REALEASE/MARKET

    private String status; // PENDING/PASSED/FAILED/SKIPPED

    private LocalDate plannedDate;

    private LocalDate actualDate;

    private String reviewer;

    private String decision; // APPROVED/REJECTED/DEFERRED

    private String comment;

    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}