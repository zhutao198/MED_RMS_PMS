package com.zhutao.medrms.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R215 v1.71: 用户偏好表（Dashboard 持久化 + 跨设备同步）
 */
@Data
@TableName("sys_schema.t_user_preference")
public class UserPreference {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 偏好键（如 dashboard.layout / dashboard.refreshInterval） */
    private String prefKey;

    /** 偏好值（JSONB 灵活结构） */
    private String prefValue;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
