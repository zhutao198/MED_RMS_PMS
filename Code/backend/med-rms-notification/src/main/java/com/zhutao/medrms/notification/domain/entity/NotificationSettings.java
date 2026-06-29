package com.zhutao.medrms.notification.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("not_schema.t_notification_settings")
public class NotificationSettings {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Boolean inAppEnabled = true;

    private Boolean emailEnabled = true;

    private Boolean smsEnabled = false;

    private Boolean wechatEnabled = false;

    private String emailAddress;

    private String phoneNumber;

    private String wechatOpenId;

    private String digestMode; // INSTANT/DAILY/WEEKLY/NONE

    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}