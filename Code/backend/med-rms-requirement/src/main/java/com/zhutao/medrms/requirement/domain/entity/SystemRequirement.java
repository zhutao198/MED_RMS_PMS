package com.zhutao.medrms.requirement.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * v1.47 BUG #131 P0 修复：SRS - 软件需求规格子表
 * 标准 CTI 模式：独立 id 主键 + requirementId 外键
 */
@Data
@TableName("req_schema.t_system_requirement")
public class SystemRequirement {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联需求 ID（外键 -> t_requirement.id） */
    private Long requirementId;

    /** 所属软件模块名 */
    private String moduleName;

    /** API规格描述 */
    private String apiSpec;

    /** 关联SOUP组件ID */
    private Long soupComponentId;

    /** 关联测试用例ID列表 (JSON) */
    private String testCaseIds;
}
