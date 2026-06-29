package com.zhutao.medrms.notification.service;

import com.zhutao.medrms.notification.domain.entity.EmailQueue;
import com.zhutao.medrms.notification.mapper.EmailQueueMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * EmailQueueService 单元测试（W12-D2）
 */
@ExtendWith(MockitoExtension.class)
class EmailQueueServiceTest {

    @Mock private EmailQueueMapper emailQueueMapper;

    @InjectMocks private EmailQueueService service;

    @Test
    @DisplayName("queueEmail-插入新邮件")
    void queueEmail() {
        when(emailQueueMapper.insert(any(EmailQueue.class))).thenReturn(1);

        EmailQueue eq = service.queueEmail("user@example.com", "Test", "Body", null);

        assertNotNull(eq);
        assertEquals("user@example.com", eq.getToAddress());
        assertEquals("PENDING", eq.getStatus());
        verify(emailQueueMapper).insert(any(EmailQueue.class));
    }

    @Test
    @DisplayName("getPendingEmails-按状态过滤")
    void getPendingEmails() {
        when(emailQueueMapper.selectList(any())).thenReturn(List.of(new EmailQueue()));
        assertEquals(1, service.getPendingEmails().size());
    }

    @Test
    @DisplayName("markAsSent-更新 status=SENT")
    void markAsSent() {
        EmailQueue eq = new EmailQueue();
        eq.setId(1L);
        eq.setStatus("PENDING");
        when(emailQueueMapper.selectById(1L)).thenReturn(eq);

        service.markAsSent(1L);

        assertEquals("SENT", eq.getStatus());
        verify(emailQueueMapper).updateById(eq);
    }

    @Test
    @DisplayName("markAsFailed-更新 status=FAILED + 写 errorMessage")
    void markAsFailed() {
        EmailQueue eq = new EmailQueue();
        eq.setId(1L);
        eq.setStatus("PENDING");
        when(emailQueueMapper.selectById(1L)).thenReturn(eq);

        service.markAsFailed(1L, "SMTP timeout");

        assertEquals("FAILED", eq.getStatus());
    }

    // ============================================================
    // W14-D3 边界用例扩充
    // ============================================================

    @Test
    @DisplayName("queueEmail-带 scheduleAt：入队时间晚于当前")
    void queueEmail_withSchedule() {
        when(emailQueueMapper.insert(any(EmailQueue.class))).thenReturn(1);

        java.time.LocalDateTime future = java.time.LocalDateTime.now().plusHours(2);
        EmailQueue eq = service.queueEmail("u@x.com", "S", "B", future);

        assertNotNull(eq);
        assertEquals("PENDING", eq.getStatus());
    }

    @Test
    @DisplayName("queueEmailCc-带 ccAddress：透传字段")
    void queueEmailCc() {
        when(emailQueueMapper.insert(any(EmailQueue.class))).thenReturn(1);

        EmailQueue eq = service.queueEmailCc("to@x.com", "cc@x.com", "subj", "body");

        assertEquals("to@x.com", eq.getToAddress());
        assertEquals("cc@x.com", eq.getCcAddress());
        assertEquals("PENDING", eq.getStatus());
    }

    @Test
    @DisplayName("markAsSent-记录不存在：静默返回（不抛错）")
    void markAsSent_notFound() {
        when(emailQueueMapper.selectById(99L)).thenReturn(null);

        service.markAsSent(99L);

        verify(emailQueueMapper, never()).updateById(any(EmailQueue.class));
    }
}
