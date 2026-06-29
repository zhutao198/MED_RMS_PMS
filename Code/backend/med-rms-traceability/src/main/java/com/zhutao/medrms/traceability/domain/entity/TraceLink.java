package com.zhutao.medrms.traceability.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v1.47 BUG #133 P0 修复：通用追溯链接实体
 * 替代原来 RequirementRelation（混用纵/横）。
 * linkType=DECOMPOSE/REFINE 时为纵向（URS->PRS->SRS->DRS 的拆解关系）；
 * linkType=DEPENDS/CONFLICTS/REUSES 时为横向（同级关联）；
 * linkType=VERIFIES 时为需求->测试用例的验证关系。
 * 一张表统一表达所有追溯链接，配合 linkType + traceContext 区分。
 */
@Data
@TableName("trace_schema.t_trace_link")
public class TraceLink {

    public static final String TYPE_DECOMPOSE = "DECOMPOSE";     // 纵向拆解
    public static final String TYPE_REFINES = "REFINES";          // 纵向精化
    public static final String TYPE_DEPENDS = "DEPENDS";          // 横向依赖
    public static final String TYPE_CONFLICTS = "CONFLICTS";      // 横向冲突
    public static final String TYPE_REUSES = "REUSES";            // 横向复用
    public static final String TYPE_VERIFIES = "VERIFIES";        // 需求->测试用例

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 链接类型 */
    private String linkType;

    /** 源端类型：REQUIREMENT/TEST_CASE */
    private String sourceType;

    /** 源端 ID */
    private Long sourceId;

    /** 源端编号（冗余） */
    private String sourceNo;

    /** 目标类型 */
    private String targetType;

    /** 目标端 ID */
    private Long targetId;

    /** 目标端编号（冗余） */
    private String targetNo;

    /** 项目 ID */
    private Long projectId;

    /** 补充上下文（如拆解说明、复用原因） */
    private String traceContext;

    /** 创建人 */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    private Boolean isDeleted = false;
}
