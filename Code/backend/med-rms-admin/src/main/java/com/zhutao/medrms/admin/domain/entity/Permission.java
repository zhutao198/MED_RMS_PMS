package com.zhutao.medrms.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限表
 */
@Data
@TableName("sys_schema.t_permission")
public class Permission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限代码 */
    private String permCode;

    /** 权限名称 */
    private String permName;

    /** 权限类型：MENU/BUTTON/API */
    private String permType;

    /** 资源路径 */
    private String resourcePath;

    /** 描述 */
    private String description;

    /** 状态：ACTIVE/INACTIVE */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;
}