package com.zhutao.medrms.requirement.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v1.47 BUG #127 P0 修复：需求版本历史
 * versionNo 改 String 支持语义化版本号（v1.0 / v1.1 / v2.0-beta 等）
 * 新增 diffSummary（diff 摘要，HTML/JSON 格式可选）和 effectiveAt（生效时间）字段
 */
@Data
@TableName("req_schema.t_requirement_version")
public class RequirementVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联需求ID */
    private Long requirementId;

    /** 语义化版本号（v1.0, v1.1, v2.0） */
    private String versionNo;

    /** 该版本需求完整快照 (JSON) */
    private String snapshot;

    /** 变更摘要（简述） */
    private String changeSummary;

    /** 变更差异（JSON 或 HTML 详细 diff，FR-1.9） */
    private String diffSummary;

    /** 变更人 */
    private Long changedBy;

    /** 变更时间 */
    private LocalDateTime changedAt;

    /** 生效时间（基线化后填入） */
    private LocalDateTime effectiveAt;
}
