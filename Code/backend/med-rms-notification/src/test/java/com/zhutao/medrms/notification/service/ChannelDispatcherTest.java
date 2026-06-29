package com.zhutao.medrms.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.notification.domain.entity.ImQueue;
import com.zhutao.medrms.notification.domain.entity.Notification;
import com.zhutao.medrms.notification.domain.entity.NotificationChannel;
import com.zhutao.medrms.notification.domain.entity.NotificationSettings;
import com.zhutao.medrms.notification.mapper.ImQueueMapper;
import com.zhutao.medrms.notification.mapper.NotificationChannelMapper;
import com.zhutao.medrms.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChannelDispatcher 单元测试（W12-D4）
 * v1.46 P1-后端-4 多渠道分发：IN_APP + EMAIL + IM
 */
@ExtendWith(MockitoExtension.class)
class ChannelDispatcherTest {

    @Mock private NotificationSettingsService settingsService;
    @Mock private NotificationChannelMapper channelMapper;
    @Mock private NotificationMapper notificationMapper;
    @Mock private EmailQueueService emailQueueService;
    @Mock private ImQueueMapper imQueueMapper;

    @InjectMocks private ChannelDispatcher service;

    private NotificationSettings newSettings(boolean inApp, boolean email, boolean wechat) {
        NotificationSettings s = new NotificationSettings();
        s.setUserId(1L);
        s.setInAppEnabled(inApp);
        s.setEmailEnabled(email);
        s.setWechatEnabled(wechat);
        s.setEmailAddress("user@example.com");
        s.setWechatOpenId("openid-123");
        return s;
    }

    private NotificationChannel newChannel(String code, String type) {
        NotificationChannel c = new NotificationChannel();
        c.setId(1L);
        c.setChannelCode(code);
        c.setChannelType(type);
        c.setChannelName(type + " 默认");
        c.setIsEnabled(true);
        c.setIsDeleted(false);
        return c;
    }

    // ============================================================
    // 1. IN_APP
    // ============================================================

    @Test
    @DisplayName("dispatch-无 settings：默认仅写 IN_APP 站内信")
    void dispatch_inAppOnly_noSettings() {
        when(settingsService.getByUser(1L)).thenReturn(null);

        service.dispatch(1L, "张三", "标题", "内容", "TASK", "REQ", 100L);

        verify(notificationMapper).insert(any(Notification.class));
        verify(emailQueueService, never()).queueEmail(any(), any(), any(), any());
        verify(imQueueMapper, never()).insert(any(ImQueue.class));
    }

    @Test
    @DisplayName("dispatch-用户禁用 IN_APP：不写站内信")
    void dispatch_inAppDisabled() {
        when(settingsService.getByUser(1L))
                .thenReturn(newSettings(false, false, false));

        service.dispatch(1L, "张三", "标题", "内容", "TASK", "REQ", 100L);

        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    // ============================================================
    // 2. EMAIL
    // ============================================================

    @Test
    @DisplayName("dispatch-EMAIL 开启 + emailAddress 有值：入队邮件")
    void dispatch_emailQueued() {
        when(settingsService.getByUser(1L))
                .thenReturn(newSettings(true, true, false));

        service.dispatch(1L, "张三", "标题", "内容", "TASK", "REQ", 100L);

        verify(notificationMapper).insert(any(Notification.class));
        verify(emailQueueService).queueEmail(
                org.mockito.ArgumentMatchers.eq("user@example.com"),
                org.mockito.ArgumentMatchers.eq("标题"),
                org.mockito.ArgumentMatchers.eq("内容"),
                any());
    }

    @Test
    @DisplayName("dispatch-EMAIL 开启但 emailAddress 为空：跳过邮件")
    void dispatch_emailNoAddress() {
        NotificationSettings s = newSettings(true, true, false);
        s.setEmailAddress(" ");
        when(settingsService.getByUser(1L)).thenReturn(s);

        service.dispatch(1L, "张三", "标题", "内容", "TASK", "REQ", 100L);

        verify(emailQueueService, never()).queueEmail(any(), any(), any(), any());
    }

    // ============================================================
    // 3. IM
    // ============================================================

    @Test
    @DisplayName("dispatch-IM 渠道开启：每个启用的 IM 渠道各入队一条")
    void dispatch_imQueued() {
        when(settingsService.getByUser(1L))
                .thenReturn(newSettings(true, false, true));
        when(channelMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        newChannel("DT", "DINGTALK"),
                        newChannel("WW", "WECHAT_WORK")
                ));

        service.dispatch(1L, "张三", "标题", "内容", "TASK", "REQ", 100L);

        ArgumentCaptor<ImQueue> captor = ArgumentCaptor.forClass(ImQueue.class);
        verify(imQueueMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        List<ImQueue> queued = captor.getAllValues();
        assertEquals(2, queued.size());
        assertEquals("openid-123", queued.get(0).getTarget());
        assertEquals("PENDING", queued.get(0).getStatus());
    }

    @Test
    @DisplayName("dispatch-IM 开启但 wechatOpenId 为空：跳过 IM")
    void dispatch_imNoOpenId() {
        NotificationSettings s = newSettings(true, false, true);
        s.setWechatOpenId(" ");
        when(settingsService.getByUser(1L)).thenReturn(s);

        service.dispatch(1L, "张三", "标题", "内容", "TASK", "REQ", 100L);

        verify(imQueueMapper, never()).insert(any(ImQueue.class));
        verify(channelMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }
}
