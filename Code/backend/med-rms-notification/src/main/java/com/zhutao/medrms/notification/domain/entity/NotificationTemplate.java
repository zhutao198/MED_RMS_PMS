package com.zhutao.medrms.notification.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("not_schema.t_notification_template")
public class NotificationTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String templateCode;

    private String templateName;

    private String channel; // IN_APP/EMAIL/SMS/WECHAT

    private String titleTemplate;

    private String contentTemplate;

    private String variables; // JSON: ["userName", "requirementNo", ...]

    private String eventType; // REVIEW_REJECTED/TRACE_BROKEN/RISK_ALERT/CHANGE_APPROVED

    private Boolean isActive = true;

    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}