package com.zhutao.medrms.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_schema.t_dict_item")
public class DictItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("dict_type")
    private String dictType;

    @TableField("item_code")
    private String dictCode;

    @TableField("item_name")
    private String dictName;

    @TableField("sort_order")
    private Integer sortOrder;

    private String status;

    @TableLogic
    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}