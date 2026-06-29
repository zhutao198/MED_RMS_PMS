package com.zhutao.medrms.requirement.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 测试用例表
 */
@Data
@TableName(value = "req_schema.t_test_case", autoResultMap = true)
public class TestCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 测试用例编号 */
    private String testCaseNo;

    /** 测试用例标题 -> test_case_name */
    @TableField("test_case_name")
    private String title;

    /** 测试类型：UNIT/INTEGRATION/SYSTEM/ACCEPTANCE */
    @TableField("test_type")
    private String testType;

    /** 测试方法 */
    @TableField("test_method")
    private String testMethod;

    /** 关联需求ID */
    @TableField("requirement_id")
    private Long requirementId;

    /** 关联需求编号 */
    @TableField("requirement_no")
    private String requirementNo;

    /** 关联项目ID */
    @TableField("project_id")
    private Long projectId;

    /** 版本 */
    private Integer version;

    /** 状态：DRAFT/ACTIVE/PASSED/FAILED/OBSOLETE */
    private String status;

    /** 测试描述 */
    private String description;

    /** 前置条件 */
    private String preCondition;

    /** 测试步骤 */
    private String testSteps;

    /** 预期结果 */
    private String expectedResult;

    /** 软件安全分类：A/B/C (IEC 62304) */
    private String safetyClass;

    /** 逻辑删除 */
    @TableLogic
    private Boolean isDeleted;

    /** 变更影响 suspect 标记（FR-0.10） */
    private Boolean isSuspect;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}