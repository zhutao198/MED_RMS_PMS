package com.zhutao.medrms.notification.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统通知渠道配置（v1.46 P1-后端-4）
 * 每条记录对应一种推送通道：EMAIL / DINGTALK / WECHAT_WORK / FEISHU 等。
 * 用户级开关见 t_notification_settings；本表是通道实现配置（webhook/AppKey/限流等）。
 */
@Data
@TableName("not_schema.t_notification_channel")
public class NotificationChannel {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 渠道编码（业务主键）：EMAIL_DEFAULT / DINGTALK_GROUP / WECHAT_WORK / FEISHU */
    private String channelCode;

    /** 显示名 */
    private String channelName;

    /** 渠道类型：EMAIL / DINGTALK / WECHAT_WORK / FEISHU */
    private String channelType;

    /** Webhook URL（机器人 / 邮件服务网关） */
    private String webhookUrl;

    /** 应用凭证（企业微信 corpId / 飞书 appId 等） */
    private String appKey;

    /** 应用密钥（钉钉加签 secret / 飞书 appSecret 等） */
    private String appSecret;

    /** 全局启用开关；false 时 dispatcher 直接跳过此渠道 */
    private Boolean isEnabled = false;

    /** 每分钟速率上限（防止触发 IM 平台限流） */
    private Integer rateLimitPerMin = 60;

    private String description;

    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
