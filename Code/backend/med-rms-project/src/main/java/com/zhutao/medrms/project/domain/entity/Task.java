package com.zhutao.medrms.project.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("proj_schema.t_task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskNo;

    private String title;

    private String description;

    private Long projectId;

    private Long assigneeId;

    private String assigneeName;

    private Long parentTaskId; // 父任务（如果是子任务）

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer estimatedHours;

    private Integer actualHours;

    private String status; // TODO/IN_PROGRESS/IN_TEST/ DONE/BLOCKED

    private String priority; // HIGH/MEDIUM/LOW

    private Long requirementId; // 关联的需求ID

    @TableField("milestone_id")
    private Long milestoneId; // 关联的里程碑ID

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}