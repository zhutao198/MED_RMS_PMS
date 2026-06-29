package com.zhutao.medrms.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.notification.domain.entity.NotificationSettings;
import com.zhutao.medrms.notification.mapper.NotificationSettingsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationSettingsService {

    private final NotificationSettingsMapper settingsMapper;

    public NotificationSettings getByUser(Long userId) {
        LambdaQueryWrapper<NotificationSettings> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationSettings::getUserId, userId)
               .eq(NotificationSettings::getIsDeleted, false);
        List<NotificationSettings> list = settingsMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    @Transactional
    public NotificationSettings createOrUpdate(Long userId, NotificationSettings settings) {
        settings.setUserId(userId);
        NotificationSettings existing = getByUser(userId);
        if (existing != null) {
            existing.setInAppEnabled(settings.getInAppEnabled());
            existing.setEmailEnabled(settings.getEmailEnabled());
            existing.setSmsEnabled(settings.getSmsEnabled());
            existing.setWechatEnabled(settings.getWechatEnabled());
            if (settings.getEmailAddress() != null) existing.setEmailAddress(settings.getEmailAddress());
            if (settings.getPhoneNumber() != null) existing.setPhoneNumber(settings.getPhoneNumber());
            if (settings.getDigestMode() != null) existing.setDigestMode(settings.getDigestMode());
            settingsMapper.updateById(existing);
            return existing;
        } else {
            settingsMapper.insert(settings);
            return settings;
        }
    }

    public boolean isChannelEnabled(Long userId, String channel) {
        NotificationSettings settings = getByUser(userId);
        if (settings == null) return true; // defaults to enabled
        return switch (channel) {
            case "EMAIL" -> settings.getEmailEnabled();
            case "SMS" -> settings.getSmsEnabled();
            case "WECHAT" -> settings.getWechatEnabled();
            default -> settings.getInAppEnabled();
        };
    }
}