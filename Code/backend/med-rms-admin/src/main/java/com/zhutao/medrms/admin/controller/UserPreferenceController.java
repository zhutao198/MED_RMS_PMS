package com.zhutao.medrms.admin.controller;

import com.zhutao.medrms.admin.service.UserPreferenceService;
import com.zhutao.medrms.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * R215 v1.71: 用户偏好 Controller（Dashboard 持久化）
 *
 * 端点：
 *  - GET /api/user/preferences          列出当前用户所有偏好
 *  - GET /api/user/preferences/{key}    获取单个偏好
 *  - GET /api/user/preferences?keys=... 批量获取
 *  - PUT /api/user/preferences/{key}    设置偏好（upsert）
 *  - DELETE /api/user/preferences/{key} 删除偏好
 */
@Tag(name = "用户偏好", description = "R215 Dashboard 持久化（跨设备同步）")
@RestController
@RequestMapping("/user/preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UserPreferenceService preferenceService;

    @Operation(summary = "列出当前用户所有偏好")
    @GetMapping
    public Result<Map<String, String>> list(@RequestParam(required = false) String keys) {
        if (keys != null && !keys.isBlank()) {
            List<String> keyList = Arrays.asList(keys.split(","));
            return Result.success(preferenceService.getCurrentUserMultiPrefs(keyList));
        }
        return Result.success(preferenceService.getCurrentUserAllPrefs());
    }

    @Operation(summary = "获取单个偏好")
    @GetMapping("/{key}")
    public Result<Map<String, String>> get(@PathVariable String key) {
        String value = preferenceService.getCurrentUserPref(key);
        return Result.success(Map.of(key, value == null ? "" : value));
    }

    @Operation(summary = "设置偏好（upsert）")
    @PutMapping("/{key}")
    public Result<String> set(@PathVariable String key, @RequestBody Map<String, Object> body) {
        Object value = body.getOrDefault("value", "");
        String valueStr = value instanceof String ? (String) value : value.toString();
        preferenceService.setCurrentUserPref(key, valueStr);
        return Result.success("OK");
    }

    @Operation(summary = "删除偏好")
    @DeleteMapping("/{key}")
    public Result<Boolean> delete(@PathVariable String key) {
        return Result.success(preferenceService.deleteCurrentUserPref(key));
    }
}
