package com.zhutao.medrms.notification.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.notification.domain.entity.EmailQueue;
import com.zhutao.medrms.notification.domain.entity.ImQueue;
import com.zhutao.medrms.notification.domain.entity.NotificationChannel;
import com.zhutao.medrms.notification.domain.entity.NotificationSettings;
import com.zhutao.medrms.notification.mapper.ImQueueMapper;
import com.zhutao.medrms.notification.mapper.NotificationChannelMapper;
import com.zhutao.medrms.notification.service.EmailQueueService;
import com.zhutao.medrms.notification.service.NotificationSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NotificationAdminController 单元测试（W14-D2）
 * v1.46 P1-后端-4 通知渠道管理 Controller
 */
@ExtendWith(MockitoExtension.class)
class NotificationAdminControllerTest {

    @Mock private EmailQueueService emailQueueService;
    @Mock private NotificationSettingsService settingsService;
    @Mock private NotificationChannelMapper channelMapper;
    @Mock private ImQueueMapper imQueueMapper;

    @InjectMocks private NotificationAdminController controller;

    // ============================================================
    // 邮件管理
    // ============================================================

    @Test
    @DisplayName("queueEmail-入队邮件")
    void queueEmail() {
        EmailQueue q = new EmailQueue();
        q.setId(1L);
        when(emailQueueService.queueEmail(anyString(), anyString(), anyString(), any()))
                .thenReturn(q);

        Result<EmailQueue> result = controller.queueEmail("to@x.com", "subj", "body", null);

        assertEquals(1L, result.getData().getId());
    }

    @Test
    @DisplayName("queueEmailCc-带抄送入队")
    void queueEmailCc() {
        EmailQueue q = new EmailQueue();
        when(emailQueueService.queueEmailCc(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(q);

        Result<EmailQueue> result = controller.queueEmailCc("to@x.com", "cc@x.com", "subj", "body");

        assertEquals(200, result.getCode());
    }

    @Test
    @DisplayName("getPendingEmails-查询待发邮件")
    void getPendingEmails() {
        when(emailQueueService.getPendingEmails()).thenReturn(List.of(new EmailQueue()));

        Result<List<EmailQueue>> result = controller.getPendingEmails();

        assertEquals(1, result.getData().size());
    }

    @Test
    @DisplayName("markAsSent-标记已发")
    void markAsSent() {
        Result<Void> result = controller.markAsSent(1L);

        assertEquals(200, result.getCode());
        verify(emailQueueService).markAsSent(1L);
    }

    @Test
    @DisplayName("markAsFailed-标记失败")
    void markAsFailed() {
        Result<Void> result = controller.markAsFailed(1L, "SMTP timeout");

        assertEquals(200, result.getCode());
        verify(emailQueueService).markAsFailed(1L, "SMTP timeout");
    }

    // ============================================================
    // 通知设置
    // ============================================================

    @Test
    @DisplayName("getSettings-获取用户通知设置")
    void getSettings() {
        NotificationSettings s = new NotificationSettings();
        s.setUserId(1L);
        when(settingsService.getByUser(1L)).thenReturn(s);

        Result<NotificationSettings> result = controller.getSettings(1L);

        assertEquals(1L, result.getData().getUserId());
    }

    @Test
    @DisplayName("saveSettings-创建或更新通知设置")
    void saveSettings() {
        NotificationSettings s = new NotificationSettings();
        when(settingsService.createOrUpdate(anyLong(), any())).thenReturn(s);

        Result<NotificationSettings> result = controller.saveSettings(1L, s);

        assertEquals(200, result.getCode());
        verify(settingsService).createOrUpdate(1L, s);
    }

    // ============================================================
    // 渠道管理
    // ============================================================

    @Test
    @DisplayName("listChannels-查询渠道列表")
    void listChannels() {
        NotificationChannel c = new NotificationChannel();
        c.setChannelCode("DINGTALK");
        when(channelMapper.selectList(any())).thenReturn(List.of(c));

        Result<List<NotificationChannel>> result = controller.listChannels();

        assertEquals(1, result.getData().size());
    }

    @Test
    @DisplayName("createChannel-创建渠道：id 自动置 null")
    void createChannel() {
        NotificationChannel c = new NotificationChannel();
        c.setId(99L); // 试图设置 id，应被清空
        c.setChannelCode("NEW");

        Result<NotificationChannel> result = controller.createChannel(c);

        assertNull(c.getId(), "Controller 应清空 id 让 MyBatis-Plus 自动生成");
        verify(channelMapper).insert(c);
    }

    @Test
    @DisplayName("updateChannel-更新渠道")
    void updateChannel() {
        NotificationChannel c = new NotificationChannel();
        c.setChannelCode("UPDATED");

        Result<NotificationChannel> result = controller.updateChannel(1L, c);

        assertEquals(1L, c.getId());
        verify(channelMapper).updateById(c);
    }

    @Test
    @DisplayName("deleteChannel-软删除渠道")
    void deleteChannel() {
        Result<Void> result = controller.deleteChannel(1L);

        assertEquals(200, result.getCode());
        verify(channelMapper).softDeleteById(1L);
    }

    // ============================================================
    // IM 队列
    // ============================================================

    @Test
    @DisplayName("getPendingIm-查询待发 IM")
    void getPendingIm() {
        ImQueue im = new ImQueue();
        when(imQueueMapper.selectList(any())).thenReturn(List.of(im));

        Result<List<ImQueue>> result = controller.getPendingIm();

        assertEquals(1, result.getData().size());
    }
}
