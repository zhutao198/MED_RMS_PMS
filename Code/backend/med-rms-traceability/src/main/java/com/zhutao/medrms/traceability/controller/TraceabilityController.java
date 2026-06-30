package com.zhutao.medrms.traceability.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.traceability.domain.entity.RequirementRelation;
import com.zhutao.medrms.traceability.domain.entity.RequirementTestCase;
import com.zhutao.medrms.traceability.service.TraceabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "追溯管理", description = "追溯矩阵、覆盖率、缺口分析接口")
@RestController
@RequestMapping("/traceability")
@RequiredArgsConstructor
public class TraceabilityController {

    private final TraceabilityService traceabilityService;

    @Operation(summary = "获取追溯矩阵（含 coverage 字段）")
    @GetMapping("/matrix")
    public Result<java.util.Map<String, Object>> getTraceMatrix(@RequestParam Long projectId) {
        // R120 P2 修复：matrix 响应顶层加 coverage 字段，避免前端再额外调 /coverage
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("rows", traceabilityService.getTraceMatrix(projectId));
        result.put("coverage", traceabilityService.getCoverageStats(projectId));
        return Result.success(result);
    }

    @Operation(summary = "获取追溯覆盖率统计")
    @GetMapping("/coverage")
    public Result<Map<String, Object>> getCoverageStats(@RequestParam Long projectId) {
        return Result.success(traceabilityService.getCoverageStats(projectId));
    }

    @Operation(summary = "获取追溯缺口分析")
    @GetMapping("/gaps")
    public Result<List<Map<String, Object>>> getTraceGaps(@RequestParam Long projectId) {
        return Result.success(traceabilityService.getTraceGaps(projectId));
    }

    @Operation(summary = "获取追溯断裂列表")
    @GetMapping("/breakages")
    public Result<List<Map<String, Object>>> getTraceBreakages(@RequestParam Long projectId) {
        return Result.success(traceabilityService.getTraceBreakages(projectId));
    }

    @Operation(summary = "添加横向关联")
    @PostMapping("/relations")
    public Result<RequirementRelation> addHorizontalRelation(
            @RequestParam Long sourceReqId,
            @RequestParam Long targetReqId,
            @RequestParam String relationType) {
        return Result.success(traceabilityService.addHorizontalRelation(sourceReqId, targetReqId, relationType));
    }

    @Operation(summary = "添加测试用例追溯")
    @PostMapping("/testcases")
    public Result<RequirementTestCase> addTestCaseTrace(
            @RequestParam Long requirementId,
            @RequestParam Long testCaseId,
            @RequestParam String traceType) {
        return Result.success(traceabilityService.addTestCaseTrace(requirementId, testCaseId, traceType));
    }

    // ===== v1.55 修复：追溯缺口忽略 =====

    @Operation(summary = "忽略一个追溯缺口")
    @PostMapping("/gaps/ignore")
    public Result<Boolean> ignoreGap(@RequestBody Map<String, Object> body) {
        Long projectId = ((Number) body.get("projectId")).longValue();
        String gapType = (String) body.get("gapType");
        Long requirementId = ((Number) body.get("requirementId")).longValue();
        String reason = (String) body.get("reason");
        return Result.success(traceabilityService.ignoreGap(projectId, gapType, requirementId, reason));
    }

    @Operation(summary = "获取项目下已忽略的缺口记录")
    @GetMapping("/gaps/ignored")
    public Result<List<Map<String, Object>>> getIgnoredGaps(@RequestParam Long projectId) {
        return Result.success(traceabilityService.getIgnoredGapRecords(projectId));
    }

    // ===== v1.55 修复：追溯数据导入 =====

    @Operation(summary = "预览/校验追溯数据导入（按行校验）")
    @PostMapping("/import/preview")
    public Result<Map<String, Object>> previewImport(
            @RequestParam Long projectId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        return Result.success(traceabilityService.previewImport(projectId, items));
    }

    @Operation(summary = "提交追溯数据导入（批量创建 TraceLink）")
    @PostMapping("/import")
    public Result<Map<String, Object>> commitImport(
            @RequestParam Long projectId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        return Result.success(traceabilityService.importBatch(projectId, items));
    }
}