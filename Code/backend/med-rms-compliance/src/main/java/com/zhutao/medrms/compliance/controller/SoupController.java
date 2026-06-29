package com.zhutao.medrms.compliance.controller;

import com.zhutao.medrms.compliance.domain.entity.SoupComponent;
import com.zhutao.medrms.compliance.service.SoupComponentService;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.risk.domain.entity.RiskAssessment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "SOUP管理", description = "SOUP组件管理接口")
@RestController
@RequestMapping("/requirement/soup-components")
@RequiredArgsConstructor
public class SoupController {

    private final SoupComponentService soupComponentService;

    @Operation(summary = "获取SOUP组件列表")
    @GetMapping
    public Result<List<SoupComponent>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel) {
        return Result.success(soupComponentService.list(status, riskLevel));
    }

    @Operation(summary = "获取SOUP组件详情")
    @GetMapping("/{id}")
    public Result<SoupComponent> getById(@PathVariable Long id) {
        return Result.success(soupComponentService.getById(id));
    }

    @Operation(summary = "创建SOUP组件")
    @PostMapping
    public Result<SoupComponent> create(@RequestBody SoupComponent component) {
        return Result.success(soupComponentService.create(component));
    }

    @Operation(summary = "更新SOUP组件")
    @PutMapping("/{id}")
    public Result<SoupComponent> update(@PathVariable Long id, @RequestBody SoupComponent component) {
        return Result.success(soupComponentService.update(id, component));
    }

    @Operation(summary = "删除SOUP组件")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        soupComponentService.delete(id);
        return Result.success();
    }

    @Operation(summary = "续期许可证")
    @PostMapping("/{id}/renew")
    public Result<SoupComponent> renewLicense(@PathVariable Long id) {
        return Result.success(soupComponentService.renewLicense(id));
    }

    @Operation(summary = "获取SOUP组件异常")
    @GetMapping("/{id}/anomalies")
    public Result<List<Map<String, Object>>> getAnomalies(@PathVariable Long id) {
        return Result.success(soupComponentService.getAnomalies(id));
    }

    @Operation(summary = "批量检测项目下所有 SOUP 异常（FR-1.11）")
    @GetMapping("/anomalies/all")
    public Result<List<Map<String, Object>>> getAllAnomalies(
            @RequestParam(required = false) Long projectId) {
        return Result.success(soupComponentService.getAllAnomalies(projectId));
    }

    @Operation(summary = "将 SOUP 异常自动关联为风险评估（FR-1.11）")
    @PostMapping("/{id}/anomalies/link-risk")
    public Result<List<RiskAssessment>> linkAnomaliesToRisk(
            @PathVariable Long id,
            @RequestParam Long requirementId,
            @RequestParam(required = false) Long assessedBy) {
        return Result.success(soupComponentService.linkAnomaliesToRisk(id, requirementId, assessedBy));
    }
}