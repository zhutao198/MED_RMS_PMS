package com.zhutao.medrms.requirement.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * v1.47 BUG #131 P0 修复：DRS - 设计需求规格子表
 * 标准 CTI 模式：独立 id 主键 + requirementId 外键
 */
@Data
@TableName("req_schema.t_design_requirement")
public class DesignRequirement {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联需求 ID（外键 -> t_requirement.id） */
    private Long requirementId;

    /** 实现负责人 */
    private String implementer;

    /** 代码仓库引用 */
    private String codeRepoRef;

    /** 代码分支 */
    private String codeBranch;
}
