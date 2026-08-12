package com.zhutao.medrms.project.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("prj_schema.t_worklog")
public class Worklog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long projectId;

    private Long requirementId;

    private Long workerId;

    private String workerName;

    private LocalDate workDate;

    private BigDecimal hours;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** P1-4 修复：21 CFR Part 11 防物理删除 */
    @TableLogic
    private Boolean isDeleted = false;
}