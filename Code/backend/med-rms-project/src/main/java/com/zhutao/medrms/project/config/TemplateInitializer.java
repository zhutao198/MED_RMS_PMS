package com.zhutao.medrms.project.config;

import com.zhutao.medrms.project.service.ComplianceTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用启动后初始化 4 个预设合规模板（FR-1.9）
 * 使用 ApplicationReadyEvent 确保 DB 已就绪
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateInitializer {

    private final ComplianceTemplateService templateService;

    @EventListener(ApplicationReadyEvent.class)
    public void onAppReady() {
        try {
            templateService.initPresetTemplates();
        } catch (Exception e) {
            log.error("初始化预设合规模板失败（可手动调用 /api/projects/templates/init 触发）", e);
        }
    }
}
