package com.zhutao.medrms.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.admin.domain.entity.UserPreference;
import com.zhutao.medrms.admin.mapper.UserPreferenceMapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * R215 v1.71: 用户偏好服务（Dashboard 持久化 + 跨设备同步）
 *
 * 工作：
 *  - get(key) → 当前用户的偏好值
 *  - set(key, value) → upsert 当前用户的偏好
 *  - getAll() → 当前用户所有偏好（Map<String, String>）
 *  - getMulti(keys) → 批量查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserPreferenceMapper preferenceMapper;

    /**
     * 获取当前登录用户的单个偏好
     */
    public String getCurrentUserPref(String key) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return null;
        UserPreference pref = preferenceMapper.selectByUserAndKey(userId, key);
        return pref != null ? pref.getPrefValue() : null;
    }

    /**
     * 获取当前登录用户的所有偏好
     */
    public Map<String, String> getCurrentUserAllPrefs() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return new HashMap<>();
        List<UserPreference> prefs = preferenceMapper.selectByUserId(userId);
        Map<String, String> map = new HashMap<>();
        for (UserPreference p : prefs) {
            map.put(p.getPrefKey(), p.getPrefValue());
        }
        return map;
    }

    /**
     * 批量获取指定 keys 的偏好
     */
    public Map<String, String> getCurrentUserMultiPrefs(List<String> keys) {
        Map<String, String> all = getCurrentUserAllPrefs();
        Map<String, String> filtered = new HashMap<>();
        for (String k : keys) {
            if (all.containsKey(k)) filtered.put(k, all.get(k));
        }
        return filtered;
    }

    /**
     * 设置当前登录用户的偏好（upsert）
     */
    @Transactional
    public UserPreference setCurrentUserPref(String key, String value) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw BusinessException.unauthorized("未登录");
        }
        UserPreference existing = preferenceMapper.selectByUserAndKey(userId, key);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            UserPreference p = new UserPreference();
            p.setUserId(userId);
            p.setPrefKey(key);
            p.setPrefValue(value);
            p.setCreatedAt(now);
            p.setUpdatedAt(now);
            preferenceMapper.insert(p);
            log.debug("R215 用户偏好创建: user={}, key={}", userId, key);
            return p;
        } else {
            existing.setPrefValue(value);
            existing.setUpdatedAt(now);
            preferenceMapper.updateById(existing);
            log.debug("R215 用户偏好更新: user={}, key={}", userId, key);
            return existing;
        }
    }

    /**
     * 删除当前登录用户的指定偏好
     */
    @Transactional
    public boolean deleteCurrentUserPref(String key) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;
        UserPreference existing = preferenceMapper.selectByUserAndKey(userId, key);
        if (existing == null) return false;
        preferenceMapper.deleteById(existing.getId());
        return true;
    }
}
