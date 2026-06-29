package com.zhutao.medrms.compliance.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.compliance.domain.entity.StatisticsSnapshot;
import com.zhutao.medrms.compliance.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 统计端点 - CQRS Lite 读取
 * 详细设计: 支撑域与通用域-详细设计.md §3.2 StatisticsController
 */
@Tag(name = "统计", description = "CQRS Lite 统计查询接口")
@RestController
@RequestMapping("/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @Operation(summary = "需求统计")
    @GetMapping("/requirements")
    public Result<Map<String, Object>> requirementStats(@RequestParam(required = false) Long projectId) {
        return Result.success(statisticsService.getRequirementStats(projectId));
    }

    @Operation(summary = "变更统计")
    @GetMapping("/changes")
    public Result<Map<String, Object>> changeStats(@RequestParam(required = false) Long projectId) {
        return Result.success(statisticsService.getChangeStats(projectId));
    }

    @Operation(summary = "风险统计")
    @GetMapping("/risks")
    public Result<Map<String, Object>> riskStats(@RequestParam(required = false) Long projectId) {
        return Result.success(statisticsService.getRiskStats(projectId));
    }

    @Operation(summary = "合规统计")
    @GetMapping("/compliance")
    public Result<Map<String, Object>> complianceStats(@RequestParam(required = false) Long projectId) {
        return Result.success(statisticsService.getComplianceStats(projectId));
    }

    @Operation(summary = "趋势数据")
    @GetMapping("/trends")
    public Result<Map<String, Object>> trends(@RequestParam(required = false) Long projectId) {
        return Result.success(statisticsService.getTrends(projectId));
    }

    @Operation(summary = "查询统计快照（读取已缓存结果）")
    @GetMapping("/snapshots")
    public Result<List<StatisticsSnapshot>> snapshots(
            @RequestParam Long projectId,
            @RequestParam String metricType) {
        return Result.success(statisticsService.getSnapshot(projectId, metricType));
    }
}
