package com.zhutao.medrms.change.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chg_schema.t_change_request")
public class ChangeRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String changeNo;

    private Long requirementId;

    private String changeType;

    private String title;

    private String description;

    private String reason;

    private String urgency;

    private String status;

    @TableField("requester_id")
    private Long requestedBy;

    @TableField("requester_name")
    private String requesterName;

    @TableField(exist = false)
    private LocalDateTime requestedAt;

    @TableField(exist = false)
    private Long approvedBy;

    @TableField(exist = false)
    private LocalDateTime approvedAt;

    @TableField(exist = false)
    private String approvalComments;

    /** 当前受派人 ID（FR-1.7 委派） */
    private Long assigneeId;

    /** 当前受派人姓名 */
    private String assigneeName;

    /** 委派来源人 ID（FR-1.7 委派审计） */
    private Long delegatedFromId;

    /** 委派来源人姓名 */
    private String delegatedFromName;

    /** 委派时间 */
    private LocalDateTime delegatedAt;

    /** 是否需要会签（FR-1.7 会签：MAJOR/EMERGENCY 默认 true） */
    private Boolean countersignRequired;

    /** 会签人列表 JSON: [{id,name,signed,signedAt,comments}] */
    private String countersigners;

    /** 会签进度：NONE/PENDING/PARTIAL/COMPLETED */
    private String countersignProgress;

    private String riskLevel;

    private String rollbackPlan;

    private String affectedItems;

    @TableField("is_deleted")
    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}