package com.zhutao.medrms.requirement.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 需求闭包表 - 用于高效层级追溯查询
 * 存储所有祖先-后代关系
 */
@Data
@TableName("req_schema.t_requirement_ancestor")
public class RequirementAncestor {

    /** 后代需求ID */
    private Long descendantId;

    /** 祖先需求ID */
    private Long ancestorId;

    /** 层级深度：0=自引用，1=直接父级，2=祖父级... */
    private Integer depth;
}