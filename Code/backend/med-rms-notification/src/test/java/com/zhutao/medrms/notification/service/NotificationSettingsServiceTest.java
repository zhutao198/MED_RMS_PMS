package com.zhutao.medrms.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.notification.domain.entity.NotificationSettings;
import com.zhutao.medrms.notification.mapper.NotificationSettingsMapper;
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
 * NotificationSettingsService 单元测试（W12-D2）
 */
@ExtendWith(MockitoExtension.class)
class NotificationSettingsServiceTest {

    @Mock private NotificationSettingsMapper mapper;

    @InjectMocks private NotificationSettingsService service;

    @Test
    @DisplayName("getByUser-不存在返回 null")
    void getByUser_default() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        NotificationSettings s = service.getByUser(100L);

        assertNull(s);
    }

    @Test
    @DisplayName("createOrUpdate-不存在则创建")
    void createOrUpdate_new() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        NotificationSettings patch = new NotificationSettings();
        patch.setInAppEnabled(true);

        NotificationSettings result = service.createOrUpdate(100L, patch);

        assertEquals(100L, result.getUserId());
        verify(mapper).insert(any(NotificationSettings.class));
    }

    // ============================================================
    // W14-D3 边界用例扩充
    // ============================================================

    @Test
    @DisplayName("getByUser-存在则返回第一条")
    void getByUser_found() {
        NotificationSettings s = new NotificationSettings();
        s.setUserId(100L);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));

        assertEquals(100L, service.getByUser(100L).getUserId());
    }

    @Test
    @DisplayName("createOrUpdate-已存在则部分字段更新")
    void createOrUpdate_update() {
        NotificationSettings existing = new NotificationSettings();
        existing.setId(1L);
        existing.setUserId(100L);
        existing.setInAppEnabled(true);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(existing));

        NotificationSettings patch = new NotificationSettings();
        patch.setEmailEnabled(false);
        patch.setEmailAddress("user@x.com");
        patch.setDigestMode("DAILY");

        NotificationSettings result = service.createOrUpdate(100L, patch);

        assertEquals(false, result.getEmailEnabled());
        assertEquals("user@x.com", result.getEmailAddress());
        assertEquals("DAILY", result.getDigestMode());
        verify(mapper).updateById(existing);
    }

    @Test
    @DisplayName("isChannelEnabled-无 settings：默认全部开启")
    void isChannelEnabled_noSettings() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertTrue(service.isChannelEnabled(100L, "EMAIL"));
        assertTrue(service.isChannelEnabled(100L, "WECHAT"));
        assertTrue(service.isChannelEnabled(100L, "IN_APP"));
    }

    @Test
    @DisplayName("isChannelEnabled-有 settings：按 channel 查询对应开关")
    void isChannelEnabled_withSettings() {
        NotificationSettings s = new NotificationSettings();
        s.setEmailEnabled(true);
        s.setSmsEnabled(false);
        s.setWechatEnabled(false);
        s.setInAppEnabled(true);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));

        assertTrue(service.isChannelEnabled(100L, "EMAIL"));
        assertFalse(service.isChannelEnabled(100L, "SMS"));
        assertFalse(service.isChannelEnabled(100L, "WECHAT"));
        assertTrue(service.isChannelEnabled(100L, "IN_APP"));
        assertTrue(service.isChannelEnabled(100L, "UNKNOWN_CHANNEL"));
    }
}

