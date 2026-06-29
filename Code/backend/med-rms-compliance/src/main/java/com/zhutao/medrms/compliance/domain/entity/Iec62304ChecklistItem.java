package com.zhutao.medrms.compliance.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * IEC 62304 合规检查清单条目
 * FR-0.15 / US-9 IEC 62304 合规检查
 */
@Data
@TableName("compliance_schema.t_iec62304_checklist")
public class Iec62304ChecklistItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目 ID */
    private Long projectId;

    /** 条款号，如 5.1.1 */
    private String clauseNo;

    /** 条款标题 */
    private String clauseTitle;

    /** 所属章节（如 §5 软件开发过程） */
    private String sectionTitle;

    /** 章节排序 */
    private Integer sectionOrder;

    /** 条款排序（章节内） */
    private Integer clauseOrder;

    /** 合规状态：COMPLIANT/PARTIAL/NON_COMPLIANT/NOT_APPLICABLE/PENDING */
    private String complianceStatus;

    /** 合规证据（满足条款的证据来源） */
    private String evidence;

    /** 差距描述（仅非合规/部分合规时） */
    private String gaps;

    /** 评估人 ID */
    private Long assessorId;

    /** 评估人姓名 */
    private String assessorName;

    /** 评估时间 */
    private LocalDateTime assessedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
