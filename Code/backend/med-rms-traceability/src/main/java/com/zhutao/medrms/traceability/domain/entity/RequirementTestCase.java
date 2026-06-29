package com.zhutao.medrms.traceability.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 需求-测试用例关联表 (横向追溯)
 */
@Data
@TableName("trace_schema.t_requirement_test_case")
public class RequirementTestCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联需求ID */
    private Long requirementId;

    /** 关联测试用例ID */
    private Long testCaseId;

    /** 追溯类型：VERIFICATION/VALIDATION */
    private String traceType;

    /** 创建时间 */
    private java.time.LocalDateTime createdAt;
}