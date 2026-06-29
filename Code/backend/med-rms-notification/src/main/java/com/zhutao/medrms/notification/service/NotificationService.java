package com.zhutao.medrms.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.notification.domain.entity.Notification;
import com.zhutao.medrms.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    // v1.46 P1-后端-4：多渠道分发（IN_APP/EMAIL/IM）
    private final ChannelDispatcher channelDispatcher;

    public void sendReviewRejectedNotification(Long userId, String userName, Long requirementId, String requirementNo) {
        channelDispatcher.dispatch(
            userId, userName,
            "需求评审未通过",
            "需求 " + requirementNo + " 评审未通过，请及时处理。",
            "REVIEW_REJECTED", "REVIEW", requirementId
        );
        log.info("发送评审不通过通知: userId={}, requirementNo={}", userId, requirementNo);
    }

    public void sendTraceBrokenNotification(Long qualityEngineerId, Long requirementId, String requirementNo) {
        channelDispatcher.dispatch(
            qualityEngineerId, null,
            "追溯链断裂告警",
            "需求 " + requirementNo + " 追溯链检测到断裂，请及时处理。",
            "TRACE_BROKEN", "REQUIREMENT", requirementId
        );
        log.info("发送追溯断裂通知: qualityEngineerId={}, requirementNo={}", qualityEngineerId, requirementNo);
    }

    public void sendRiskAlertNotification(Long userId, String requirementNo, String riskLevel) {
        channelDispatcher.dispatch(
            userId, null,
            "高风险需求告警",
            "需求 " + requirementNo + " 风险等级为: " + riskLevel + "，请关注。",
            "RISK_ALERT", "RISK", null
        );
    }

    public void sendChangeApprovedNotification(Long userId, String changeNo) {
        channelDispatcher.dispatch(
            userId, null,
            "变更已批准",
            "变更申请 " + changeNo + " 已批准。",
            "CHANGE_APPROVED", "CHANGE", null
        );
    }

    public void sendReviewApprovedNotification(Long userId, Long requirementId, String requirementNo) {
        channelDispatcher.dispatch(
            userId, null,
            "需求评审已通过",
            "需求 " + requirementNo + " 评审已通过，可继续后续流程。",
            "REVIEW_APPROVED", "REQUIREMENT", requirementId
        );
        log.info("发送评审通过通知: userId={}, requirementNo={}", userId, requirementNo);
    }

    public void sendSystemNotification(Long userId, String title, String content, String sourceType, Long sourceId) {
        channelDispatcher.dispatch(
            userId, null, title, content, "SYSTEM", sourceType, sourceId
        );
        log.info("发送系统通知: userId={}, title={}", userId, title);
    }

    public List<Notification> getUnreadByUser(Long userId) {
        return notificationMapper.selectList(
            new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getStatus, "UNREAD")
                .orderByDesc(Notification::getCreatedAt)
        );
    }

    public int getUnreadCount(Long userId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        wrapper.eq(Notification::getStatus, "UNREAD");
        Long count = notificationMapper.selectCount(wrapper);
        return count != null ? count.intValue() : 0;
    }

    public List<Notification> getAllByUser(Long userId, String status, String type) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(Notification::getStatus, status);
        }
        if (type != null && !type.isBlank()) {
            wrapper.eq(Notification::getType, type);
        }
        wrapper.orderByDesc(Notification::getCreatedAt);
        return notificationMapper.selectList(wrapper);
    }

    public void markAsRead(Long notificationId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification != null && "UNREAD".equals(notification.getStatus())) {
            notification.setStatus("READ");
            notification.setReadAt(LocalDateTime.now());
            notificationMapper.updateById(notification);
        }
    }

    public void markAllAsRead(Long userId) {
        List<Notification> unread = getUnreadByUser(userId);
        for (Notification n : unread) {
            n.setStatus("READ");
            n.setReadAt(LocalDateTime.now());
            notificationMapper.updateById(n);
        }
    }

    public void deleteNotification(Long notificationId) {
        notificationMapper.deleteById(notificationId);
    }

    public void deleteByUser(Long userId) {
        notificationMapper.delete(
            new LambdaQueryWrapper<Notification>().eq(Notification::getUserId, userId)
        );
    }
}
