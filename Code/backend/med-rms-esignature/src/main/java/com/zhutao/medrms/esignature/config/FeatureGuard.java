package com.zhutao.medrms.esignature.config;

import com.zhutao.medrms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * R220 v1.76: Feature Guard — 统一功能开关运行时检查
 *
 * 在 Controller / Service 入口调用：
 *  - guard.requireSignatureEnabled() — 签名功能禁用时抛 SY0503
 *  - guard.isSignatureEnabled() — 查询（不抛）
 *
 * 错误码：SY0503 = 服务暂时不可用（功能被禁用）
 */
@Component
@RequiredArgsConstructor
public class FeatureGuard {

    private final ComplianceFeatureConfig config;

    public boolean isSignatureEnabled() {
        return config.getModules().isSignature();
    }

    public void requireSignatureEnabled() {
        if (!isSignatureEnabled()) {
            throw new BusinessException("SY0503",
                "电子签名功能已临时禁用（R220）。请联系管理员启用 compliance.modules.signature 配置。");
        }
    }
}
