package com.zhutao.medrms.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_schema.t_system_config")
public class SystemConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configName;

    private String configKey;

    private String configValue;

    private String description;

    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}