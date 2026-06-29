package com.zhutao.medrms.change.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v1.47 BUG #111 P0 修复：变更执行记录
 * 一次变更可能分批执行（例：受影响的 5 个需求逐步更新），每次执行一条记录
 * 记录受影响需求 before/after 版本快照
 */
@Data
@TableName("chg_schema.t_change_execution")
public class ChangeExecution {

    public static final String STATUS_EXECUTING = "EXECUTING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long changeId;

    /** 受影响需求 ID */
    private Long requirementId;

    /** 受影响需求编号 */
    private String requirementNo;

    /** 旧版本号 */
    private String oldVersion;

    /** 新版本号 */
    private String newVersion;

    /** 旧内容（JSON 字符串） */
    private String oldSnapshot;

    /** 新内容（JSON 字符串） */
    private String newSnapshot;

    private String status;

    /** 执行人 userId */
    private Long executorId;

    private String executorName;

    private LocalDateTime executedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    private String remarks;

    private LocalDateTime createdAt;
}
