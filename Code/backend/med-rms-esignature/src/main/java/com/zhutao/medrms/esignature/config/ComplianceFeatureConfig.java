package com.zhutao.medrms.esignature.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * R220 v1.76: 合规功能 Feature Flag 配置（电子签名模块）
 *
 * 用法：
 *  - application.yml: compliance.modules.signature: false
 *  - 启动时 Spring 自动绑定到此对象
 *  - FeatureGuard.isSignatureEnabled() 返回 false → 签名相关 API 抛 503
 *
 * 恢复方式：application.yml 改 compliance.modules.signature: true
 */
@Data
@Component
@ConfigurationProperties(prefix = "compliance")
public class ComplianceFeatureConfig {

    private Modules modules = new Modules();

    @Data
    public static class Modules {
        /** R220: 电子签名（默认 true，application.yml 可覆盖为 false） */
        private boolean signature = true;
    }
}
