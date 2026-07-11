package com.zhutao.medrms.project.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("proj_schema.t_project_activity")
public class ProjectActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String activityType;

    private String summary;

    private String detail;

    private Long operatorId;

    private String operatorName;

    private String sourceType;

    private Long sourceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
