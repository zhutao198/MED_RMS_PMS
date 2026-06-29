package com.zhutao.medrms.requirement.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * v1.47 BUG #131 P0 修复：URS - 用户需求规格子表
 * 标准 CTI 模式：独立 id 主键 + requirementId 外键关联 t_requirement
 */
@Data
@TableName("req_schema.t_user_requirement")
public class UserRequirement {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联需求 ID（外键 -> t_requirement.id） */
    private Long requirementId;

    /** 关联法规条款编号列表 (JSON) */
    private String regulationRefs;

    /** URS验收标准 */
    private String acceptanceCriteria;

    /** 来源：INTERNAL/REGULATORY/USER */
    private String origin;
}
