package com.zhutao.medrms.notification.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.common.util.SecurityUtils;
import com.zhutao.medrms.notification.domain.entity.Notification;
import com.zhutao.medrms.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * NotificationController 单元测试（v1.27 R28 + P0-1 修复后适配）
 * P0-1 修复：userId 从 SecurityUtils.getCurrentUserId() 取，本测试用 MockedStatic 模拟
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController controller;

    @Test
    void getUnread_returnsList() {
        Notification n = new Notification();
        n.setId(1L);
        n.setTitle("unread");
        when(notificationService.getUnreadByUser(1L)).thenReturn(Arrays.asList(n));

        Result<List<Notification>> result;
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class, CALLS_REAL_METHODS)) {
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            result = controller.getUnread();
        }

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    void getUnreadCount_returnsMap() {
        when(notificationService.getUnreadCount(1L)).thenReturn(5);

        Result<Map<String, Integer>> result;
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class, CALLS_REAL_METHODS)) {
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            result = controller.getUnreadCount();
        }

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(5, result.getData().get("count"));
    }

    @Test
    void markAsRead_returnsSuccess() {
        Result<Void> result;
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class, CALLS_REAL_METHODS)) {
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            result = controller.markAsRead(1L);
        }

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(notificationService, times(1)).markAsRead(1L, 1L);
    }

    @Test
    void markAllAsRead_returnsSuccess() {
        Result<Void> result;
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class, CALLS_REAL_METHODS)) {
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            result = controller.markAllAsRead();
        }

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(notificationService, times(1)).markAllAsRead(1L);
    }
}
