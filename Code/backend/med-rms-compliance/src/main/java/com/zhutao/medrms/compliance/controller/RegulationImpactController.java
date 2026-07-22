package com.zhutao.medrms.compliance.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.compliance.service.RegulationImpactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * R213 v1.69: 法规更新推送 Controller（FR-2.2 PRD §7.5.3）
 */
@Slf4j
@Tag(name = "法规更新推送", description = "FR-2.2 法规更新自动分析影响 + 通知")
@RestController
@RequestMapping("/regulations")
@RequiredArgsConstructor
public class RegulationImpactController {

    private final RegulationImpactService regulationImpactService;

    @PostConstruct
    public void init() {
        regulationImpactService.initBuiltinRegulations();
    }

    /**
     * 列出法规库（内置）
     */
    @Operation(summary = "列出内置法规库")
    @GetMapping("/list")
    public Result<Map<String, Map<String, RegulationImpactService.RegulationInfo>>> list() {
        return Result.success(regulationImpactService.listRegulations());
    }

    /**
     * 法规更新触发（影响分析 + 通知）
     */
    @Operation(summary = "法规更新推送（FR-2.2 核心）")
    @PostMapping("/notify-update")
    public Result<RegulationImpactService.ImpactResult> notifyUpdate(
            @RequestBody NotifyUpdateRequest req) {
        log.info("R213 法规更新推送请求: {}", req);
        return Result.success(regulationImpactService.notifyRegulationUpdate(
            req.getRegulationType(), req.getClauseNumber(),
            req.getNewVersion(), req.getUpdatedBy()));
    }

    /**
     * 查询受影响需求
     */
    @Operation(summary = "查询受影响需求")
    @GetMapping("/impact/{regulationType}/{clauseNumber}")
    public Result<RegulationImpactService.ImpactResult> queryImpact(
            @PathVariable String regulationType,
            @PathVariable String clauseNumber,
            @RequestParam(required = false, defaultValue = "preview") String version,
            @RequestParam(required = false, defaultValue = "preview-user") String updatedBy) {
        // 查询时不实际发通知（updatedBy=preview-user 表示仅预览）
        RegulationImpactService.ImpactResult result = regulationImpactService.notifyRegulationUpdate(
            regulationType, clauseNumber, version, updatedBy);
        return Result.success(result);
    }

    @lombok.Data
    public static class NotifyUpdateRequest {
        private String regulationType;
        private String clauseNumber;
        private String newVersion;
        private String updatedBy;
    }
}
