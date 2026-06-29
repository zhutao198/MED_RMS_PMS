package com.zhutao.medrms.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色权限关联表
 */
@Data
@TableName("sys_schema.t_role_permission")
public class RolePermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色ID */
    private Long roleId;

    /** 权限ID */
    private Long permId;

    /** 创建时间 */
    private LocalDateTime createdAt;
}