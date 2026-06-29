package com.zhutao.medrms.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.notification.domain.entity.ImQueue;
import com.zhutao.medrms.notification.domain.entity.Notification;
import com.zhutao.medrms.notification.domain.entity.NotificationChannel;
import com.zhutao.medrms.notification.domain.entity.NotificationSettings;
import com.zhutao.medrms.notification.mapper.ImQueueMapper;
import com.zhutao.medrms.notification.mapper.NotificationChannelMapper;
import com.zhutao.medrms.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知渠道分发器（v1.46 P1-后端-4）
 *
 * 流程：
 *   1. 读取 t_notification_settings → 得到用户级开关与目标地址（email/openId/phone）
 *   2. 读取 t_notification_channel  → 得到系统级可用渠道（webhook/限流/启用）
 *   3. 笛卡尔组合：每对 (用户渠道启用, 系统渠道启用) 都写一条
 *      - IN_APP    → t_notification
 *      - EMAIL     → t_email_queue
 *      - DINGTALK/WECHAT_WORK/FEISHU → t_im_queue
 *
 * 不直接 HTTP 调用：发往 IM 平台由 worker 异步消费 t_im_queue 执行（避免阻塞业务事务
 * + 平台限流控制 + 重试）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelDispatcher {

    private final NotificationSettingsService settingsService;
    private final NotificationChannelMapper channelMapper;
    private final NotificationMapper notificationMapper;
    private final EmailQueueService emailQueueService;
    private final ImQueueMapper imQueueMapper;

    /** 通用业务通知（替代 NotificationService 内部直接 insert） */
    @Transactional
    public void dispatch(Long userId,
                         String userName,
                         String title,
                         String content,
                         String type,
                         String sourceType,
                         Long sourceId) {
        NotificationSettings settings = settingsService.getByUser(userId);
        boolean inAppOn = settings == null || Boolean.TRUE.equals(settings.getInAppEnabled());
        boolean emailOn = settings != null && Boolean.TRUE.equals(settings.getEmailEnabled());
        boolean wechatOn = settings != null && Boolean.TRUE.equals(settings.getWechatEnabled());

        // 1) IN_APP 站内信（默认开启，未配置 settings 也开）
        if (inAppOn) {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setUserName(userName);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setType(type);
            notification.setStatus("UNREAD");
            notification.setSourceType(sourceType);
            notification.setSourceId(sourceId);
            notification.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(notification);
            log.debug("[IN_APP] 站内信已写入: userId={}, type={}", userId, type);
        }

        // 2) EMAIL 邮件（需用户在 settings 中开启 + 填邮箱）
        if (emailOn && settings != null
                && settings.getEmailAddress() != null && !settings.getEmailAddress().isBlank()) {
            emailQueueService.queueEmail(
                    settings.getEmailAddress(),
                    title,
                    content,
                    LocalDateTime.now()
            );
            log.info("[EMAIL] 邮件已入队: to={}, type={}", settings.getEmailAddress(), type);
        }

        // 3) IM 渠道：复用 wechatOpenId 作为通用 IM target（v1.46 简化；后续按渠道拆分）
        if (wechatOn && settings != null
                && settings.getWechatOpenId() != null && !settings.getWechatOpenId().isBlank()) {
            // 注：使用 in(...) 而非 or().eq()，避免 MyBatis-Plus LambdaQueryWrapper 的 .or()
            // 改变 AND 优先级（会生成 `(A) OR (B AND C)` 而非 `(A OR B OR C) AND C`）
            List<NotificationChannel> imChannels = channelMapper.selectList(
                    new LambdaQueryWrapper<NotificationChannel>()
                            .in(NotificationChannel::getChannelType, "DINGTALK", "WECHAT_WORK", "FEISHU")
                            .eq(NotificationChannel::getIsEnabled, true)
                            .eq(NotificationChannel::getIsDeleted, false)
            );
            for (NotificationChannel ch : imChannels) {
                ImQueue im = new ImQueue();
                im.setChannelCode(ch.getChannelCode());
                im.setTarget(settings.getWechatOpenId());
                im.setTargetType("USER");
                im.setTitle(title);
                im.setContent(content);
                im.setStatus("PENDING");
                im.setRelatedUserId(userId);
                im.setRelatedType(type);
                im.setRelatedId(sourceId);
                imQueueMapper.insert(im);
                log.info("[IM:{}] IM 消息已入队: userId={}, type={}", ch.getChannelType(), userId, type);
            }
        }
    }
}
