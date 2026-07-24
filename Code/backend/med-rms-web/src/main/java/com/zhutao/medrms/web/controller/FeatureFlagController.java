package com.zhutao.medrms.web.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.esignature.config.ComplianceFeatureConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * R221 v1.77: Feature Flag 公开端点（前端用）
 *
 * 端点：GET /api/feature/flags（无需认证，前端启动时调用）
 * 返回当前生效的合规功能开关
 */
@Tag(name = "Feature Flag", description = "R221 合规功能开关公开端点")
@RestController
@RequestMapping("/feature")
@RequiredArgsConstructor
public class FeatureFlagController {

    private final ComplianceFeatureConfig config;

    @Operation(summary = "获取当前 Feature Flags（前端启动时调用）")
    @GetMapping("/flags")
    public Result<Map<String, Boolean>> flags() {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        flags.put("signature", config.getModules().isSignature());
        // 后续模块可加
        // flags.put("dhf", config.getModules().isDHF());
        // flags.put("erps", config.getModules().isERPS());
        return Result.success(flags);
    }
}
