package com.zhutao.medrms.project.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 项目交付物（FR 配套模块）
 * R92 新增：替代 R84 临时挂"功能开发中"提示的 ProjectDeliverables 页面
 */
@Data
@TableName("proj_schema.t_project_deliverable")
public class ProjectDeliverable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String name;

    /** 文档 DOC / 代码 CODE / 测试用例 TEST_CASE / 报告 REPORT / 基线 BASELINE */
    private String type;

    /** 所属阶段：开发/测试/发布 等 */
    private String phase;

    /** 状态：TODO 待开始 / IN_PROGRESS 进行中 / DONE 已完成 / BLOCKED 阻塞 */
    private String status;

    private Long ownerId;

    private String ownerName;

    private LocalDate dueDate;

    private String description;

    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
