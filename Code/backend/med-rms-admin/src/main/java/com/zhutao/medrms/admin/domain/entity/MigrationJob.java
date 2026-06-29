package com.zhutao.medrms.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("admin_schema.t_migration_job")
public class MigrationJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobName;

    private String jobType; // IMPORT_REQ / IMPORT_REQ_CSV / ...

    private String status; // RUNNING / SUCCESS / FAILED / PARTIAL

    private Long operatorId;

    private Integer totalCount;

    private Integer successCount;

    private Integer failureCount;

    private String errorLog;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
