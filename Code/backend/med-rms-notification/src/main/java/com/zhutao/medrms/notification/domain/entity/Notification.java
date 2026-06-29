package com.zhutao.medrms.notification.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("not_schema.t_notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String userName;

    private String title;

    private String content;

    private String type; // REVIEW_REJECTED/TRACE_BROKEN/RISK_ALERT/CHANGE_APPROVED/SYSTEM

    private String status; // UNREAD/READ

    private String sourceType; // REVIEW/REQUIREMENT/CHANGE/RISK

    private Long sourceId;

    private LocalDateTime readAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}