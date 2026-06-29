package com.zhutao.medrms.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_schema.t_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    @JsonIgnore
    private String passwordHash;

    private String realName;

    private String email;

    private String phone;

    private String department;

    /** R99 组织架构-部门外键（双轨：department 字符串仍可用） */
    private Long deptId;

    private String role;

    private String status;

    @JsonIgnore
    private String signaturePasswordHash;

    private LocalDateTime signaturePasswordExpiredAt;

    private LocalDateTime lastLoginAt;

    private Long createdBy;

    private LocalDateTime createdAt;

    private Long updatedBy;

    private LocalDateTime updatedAt;

    @TableLogic
    private Boolean isDeleted;
}