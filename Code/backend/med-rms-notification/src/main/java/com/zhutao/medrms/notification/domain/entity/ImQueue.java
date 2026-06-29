package com.zhutao.medrms.notification.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 推送队列（v1.46 P1-后端-4）
 * 与 t_email_queue 对齐：用于记录每次 IM 推送的发送状态、重试次数和错误信息，
 * 由后台 worker 拉取并调用对应平台 HTTP API 实际下发。
 */
@Data
@TableName("not_schema.t_im_queue")
public class ImQueue {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 t_notification_channel.channel_code */
    private String channelCode;

    /** 接收者：用户 openId / unionId / 群 webhook chatId */
    private String target;

    /** USER / GROUP */
    private String targetType = "USER";

    private String title;

    private String content;

    /** PENDING / SENT / FAILED */
    private String status = "PENDING";

    private Integer retryCount = 0;

    private String errorMessage;

    /** 接收方业务用户 ID（与 sys_schema.t_user.id 关联，便于对账） */
    private Long relatedUserId;

    /** 业务类型：REVIEW_REJECTED / TRACE_BROKEN / RISK_ALERT / ... */
    private String relatedType;

    private Long relatedId;

    private LocalDateTime sentAt;

    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
