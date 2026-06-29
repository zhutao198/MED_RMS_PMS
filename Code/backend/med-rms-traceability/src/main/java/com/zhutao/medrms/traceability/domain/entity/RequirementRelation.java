package com.zhutao.medrms.traceability.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 需求横向关联表 (横向追溯)
 */
@Data
@TableName("trace_schema.t_requirement_relation")
public class RequirementRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 源需求ID */
    private Long sourceReqId;

    /** 目标需求ID */
    private Long targetReqId;

    /** 关联类型：DEPENDS/CONFLICTS/REUSES (直接存储横向类型) */
    private String relationType;

    /** 项目ID */
    private Long projectId;

    /** 创建时间 */
    private java.time.LocalDateTime createdAt;

    private Boolean isDeleted = false;
}