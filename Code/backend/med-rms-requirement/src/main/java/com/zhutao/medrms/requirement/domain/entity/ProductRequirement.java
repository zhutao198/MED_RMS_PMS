package com.zhutao.medrms.requirement.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * v1.47 BUG #131 P0 修复：PRS - 产品需求规格子表
 * 标准 CTI 模式：独立 id 主键 + requirementId 外键
 */
@Data
@TableName("req_schema.t_product_requirement")
public class ProductRequirement {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联需求 ID（外键 -> t_requirement.id） */
    private Long requirementId;

    /** 性能目标描述 */
    private String performanceTarget;

    /** 接口规格文档引用 */
    private String interfaceSpecRef;

    /** 验证方法：TEST/ANALYSIS/INSPECTION/DEMONSTRATION */
    private String verificationMethod;
}
