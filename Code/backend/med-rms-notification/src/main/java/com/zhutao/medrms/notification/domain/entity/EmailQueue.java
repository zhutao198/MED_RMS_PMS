package com.zhutao.medrms.notification.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("not_schema.t_email_queue")
public class EmailQueue {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String toAddress;

    private String ccAddress;

    private String subject;

    private String body;

    private String status; // PENDING/SENT/FAILED

    private Integer retryCount = 0;

    private String errorMessage;

    private LocalDateTime sentAt;

    private LocalDateTime scheduledAt;

    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}