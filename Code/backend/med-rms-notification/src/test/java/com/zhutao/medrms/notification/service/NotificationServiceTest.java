package com.zhutao.medrms.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.notification.service.ChannelDispatcher;
import com.zhutao.medrms.notification.domain.entity.Notification;
import com.zhutao.medrms.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NotificationService 单元测试（W2-D7）
 * 覆盖：6 种发送通知（review approved/rejected/trace broken/risk/change/system）
 *      /未读/全部/标记已读/删除
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationMapper notificationMapper;
    @Mock private ChannelDispatcher channelDispatcher;

    @InjectMocks private NotificationService service;

    // ============================================================
    // 1. 发送通知（6 种）
    // ============================================================

    @Test
    @DisplayName("sendReviewApprovedNotification-调用 channelDispatcher")
    void sendReviewApproved() {
        service.sendReviewApprovedNotification(100L, 1L, "REQ-001");
        verify(channelDispatcher).dispatch(eq(100L), isNull(), anyString(), anyString(),
            eq("REVIEW_APPROVED"), eq("REQUIREMENT"), eq(1L));
    }

    @Test
    @DisplayName("sendReviewRejectedNotification-调用 channelDispatcher")
    void sendReviewRejected() {
        service.sendReviewRejectedNotification(100L, "alice", 1L, "REQ-001");
        verify(channelDispatcher).dispatch(eq(100L), eq("alice"), anyString(), anyString(),
            eq("REVIEW_REJECTED"), eq("REVIEW"), eq(1L));
    }

    @Test
    @DisplayName("sendTraceBrokenNotification-质量工程师 ID")
    void sendTraceBroken() {
        service.sendTraceBrokenNotification(200L, 1L, "REQ-001");
        verify(channelDispatcher).dispatch(eq(200L), isNull(), anyString(), anyString(),
            eq("TRACE_BROKEN"), eq("REQUIREMENT"), eq(1L));
    }

    @Test
    @DisplayName("sendRiskAlertNotification-HIGH 风险")
    void sendRiskAlert() {
        service.sendRiskAlertNotification(100L, "REQ-001", "HIGH");
        verify(channelDispatcher).dispatch(eq(100L), isNull(), anyString(), contains("HIGH"),
            eq("RISK_ALERT"), eq("RISK"), isNull());
    }

    @Test
    @DisplayName("sendChangeApprovedNotification-变更编号")
    void sendChangeApproved() {
        service.sendChangeApprovedNotification(100L, "CR-1-0001");
        verify(channelDispatcher).dispatch(eq(100L), isNull(), anyString(), anyString(),
            eq("CHANGE_APPROVED"), eq("CHANGE"), isNull());
    }

    @Test
    @DisplayName("sendSystemNotification-通用通知")
    void sendSystem() {
        service.sendSystemNotification(100L, "标题", "内容", "SYSTEM", 999L);
        verify(channelDispatcher).dispatch(eq(100L), isNull(), eq("标题"), eq("内容"),
            anyString(), eq("SYSTEM"), eq(999L));
    }

    // ============================================================
    // 2. 查询
    // ============================================================

    @Test
    @DisplayName("getUnreadByUser-透传 mapper")
    void getUnread() {
        when(notificationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(new Notification()));
        org.junit.jupiter.api.Assertions.assertEquals(1, service.getUnreadByUser(100L).size());
    }

    @Test
    @DisplayName("getUnreadCount-返回数字")
    void getUnreadCount() {
        when(notificationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);
        org.junit.jupiter.api.Assertions.assertEquals(5, service.getUnreadCount(100L));
    }

    @Test
    @DisplayName("getAllByUser-透传 mapper")
    void getAll() {
        when(notificationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(new Notification()));
        org.junit.jupiter.api.Assertions.assertEquals(1, service.getAllByUser(100L, null, null).size());
    }

    // ============================================================
    // 3. 标记 / 删除
    // ============================================================

    @Test
    @DisplayName("markAsRead-UNREAD → READ + 写 readAt")
    void markRead() {
        Notification n = new Notification();
        n.setId(1L);
        n.setStatus("UNREAD");
        when(notificationMapper.selectById(1L)).thenReturn(n);

        service.markAsRead(1L);

        verify(notificationMapper).updateById(n);
        assertEquals("READ", n.getStatus());
        assertNotNull(n.getReadAt());
    }

    @Test
    @DisplayName("markAsRead-已 READ 跳过")
    void markRead_skip() {
        Notification n = new Notification();
        n.setId(1L);
        n.setStatus("READ");
        when(notificationMapper.selectById(1L)).thenReturn(n);

        service.markAsRead(1L);

        verify(notificationMapper, never()).updateById(any(Notification.class));
    }

    @Test
    @DisplayName("markAllAsRead-批量更新")
    void markAllRead() {
        Notification n1 = new Notification();
        n1.setId(1L); n1.setStatus("UNREAD");
        Notification n2 = new Notification();
        n2.setId(2L); n2.setStatus("UNREAD");
        when(notificationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(n1, n2));

        service.markAllAsRead(100L);

        verify(notificationMapper, times(2)).updateById(any(Notification.class));
    }

    @Test
    @DisplayName("deleteNotification-调用 mapper")
    void delete() {
        service.deleteNotification(1L);
        verify(notificationMapper).deleteById(1L);
    }

    @Test
    @DisplayName("deleteByUser-调用 mapper（LambdaQueryWrapper）")
    void deleteByUser() {
        service.deleteByUser(100L);
        verify(notificationMapper).delete(any(LambdaQueryWrapper.class));
    }

    // 加 jupiter Assertions 静态导入
    static {
        // 确保 Assertions 方法被 import
    }
}
