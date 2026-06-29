package com.zhutao.medrms.traceability.controller;

import com.zhutao.medrms.common.annotation.AuditLog;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.traceability.domain.entity.TraceLink;
import com.zhutao.medrms.traceability.service.TraceabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * v1.47 BUG #134 P0 修复：TraceLink 9 个 CRUD 端点
 * 提供追溯链接的完整管理能力（创建/更新/删除/查询）
 */
@Tag(name = "追溯链接", description = "通用追溯链接 CRUD（v1.47 BUG #133/134）")
@RestController
@RequestMapping("/trace-links")
@RequiredArgsConstructor
public class TraceLinkController {

    private final TraceabilityService traceabilityService;

    @Operation(summary = "创建追溯链接（带无环校验）")
    @PostMapping
    @AuditLog(eventType = "CREATE", entityType = "TRACE_LINK", operation = "创建追溯链接")
    public Result<TraceLink> createTraceLink(@RequestBody TraceLink link) {
        return Result.success(traceabilityService.createTraceLink(link));
    }

    @Operation(summary = "更新追溯链接（context/linkType）")
    @PutMapping("/{id}")
    @AuditLog(eventType = "MODIFY", entityType = "TRACE_LINK", operation = "更新追溯链接", entityIdSpel = "#id")
    public Result<Boolean> updateTraceLink(@PathVariable Long id, @RequestBody TraceLink patch) {
        return Result.success(traceabilityService.updateTraceLink(id, patch));
    }

    @Operation(summary = "软删除追溯链接")
    @DeleteMapping("/{id}")
    @AuditLog(eventType = "DELETE", entityType = "TRACE_LINK", operation = "删除追溯链接", entityIdSpel = "#id")
    public Result<Boolean> deleteTraceLink(@PathVariable Long id) {
        return Result.success(traceabilityService.deleteTraceLink(id));
    }

    @Operation(summary = "获取追溯链接详情")
    @GetMapping("/{id}")
    public Result<TraceLink> getTraceLinkById(@PathVariable Long id) {
        return Result.success(traceabilityService.getTraceLinkById(id));
    }

    @Operation(summary = "按项目+类型列出追溯链接")
    @GetMapping
    public Result<List<TraceLink>> listTraceLinks(
            @RequestParam Long projectId,
            @RequestParam(required = false) String linkType) {
        return Result.success(traceabilityService.listTraceLinks(projectId, linkType));
    }

    @Operation(summary = "按源端 ID 查询追溯链接")
    @GetMapping("/by-source/{sourceId}")
    public Result<List<TraceLink>> listBySource(@PathVariable Long sourceId) {
        return Result.success(traceabilityService.listBySource(sourceId));
    }

    @Operation(summary = "按目标 ID 查询追溯链接")
    @GetMapping("/by-target/{targetId}")
    public Result<List<TraceLink>> listByTarget(@PathVariable Long targetId) {
        return Result.success(traceabilityService.listByTarget(targetId));
    }

    @Operation(summary = "无环校验（添加前预检）")
    @GetMapping("/check-cycle")
    public Result<Boolean> checkCycle(
            @RequestParam Long sourceId,
            @RequestParam Long targetId) {
        return Result.success(traceabilityService.wouldCreateCycle(sourceId, targetId));
    }

    // v1.55 修复：按 (source, target) 对查询追溯链接（TraceMatrix 详情对话框用）
    @Operation(summary = "按 (source, target) 对查询追溯链接")
    @GetMapping("/by-pair")
    public Result<List<TraceLink>> listByPair(
            @RequestParam Long sourceId,
            @RequestParam Long targetId) {
        return Result.success(traceabilityService.listByPair(sourceId, targetId));
    }
}
