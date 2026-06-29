package com.zhutao.medrms.traceability.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.traceability.service.TraceGraphService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "追溯图谱", description = "追溯链路可视化与质量评分接口")
@RestController
@RequestMapping("/trace-graph")
@RequiredArgsConstructor
public class TraceGraphController {

    private final TraceGraphService traceGraphService;

    @Operation(summary = "获取追溯图谱数据")
    @GetMapping("/project/{projectId}")
    public Result<Map<String, Object>> getTraceGraph(@PathVariable Long projectId) {
        return Result.success(traceGraphService.getTraceGraph(projectId));
    }

    @Operation(summary = "获取需求质量评分")
    @GetMapping("/quality/{requirementId}")
    public Result<Map<String, Object>> getQualityScore(@PathVariable Long requirementId) {
        return Result.success(traceGraphService.getQualityScore(requirementId));
    }

    @Operation(summary = "批量获取质量评分")
    @GetMapping("/quality/batch")
    public Result<List<Map<String, Object>>> batchQualityScore(@RequestParam Long projectId) {
        return Result.success(traceGraphService.getBatchQualityScore(projectId));
    }
}