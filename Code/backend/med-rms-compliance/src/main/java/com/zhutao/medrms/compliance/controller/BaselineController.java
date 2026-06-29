package com.zhutao.medrms.compliance.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.compliance.domain.entity.Baseline;
import com.zhutao.medrms.compliance.service.BaselineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * v1.48 P0 #2 修复：基线 Controller 从 med-rms-requirement 迁移到 med-rms-compliance
 */
@Tag(name = "基线管理", description = "基线创建、对比、双签锁定接口")
@RestController
@RequestMapping("/baselines")
@RequiredArgsConstructor
public class BaselineController {

    private final BaselineService baselineService;

    @Operation(summary = "创建基线")
    @PostMapping
    public Result<Baseline> create(@RequestBody CreateBaselineRequest request) {
        return Result.success(baselineService.createBaseline(
            request.getProjectId(),
            request.getName(),
            request.getRequirementIds()
        ));
    }

    @Operation(summary = "基线化需求（v1.48 P0 #2 修复：从 RequirementService 迁入）")
    @PostMapping("/baseline-requirements")
    public Result<Void> baselineRequirements(
            @RequestParam Long baselineId,
            @RequestBody List<Long> requirementIds) {
        baselineService.baselineRequirements(baselineId, requirementIds);
        return Result.success();
    }

    @Operation(summary = "获取项目基线列表")
    @GetMapping("/project/{projectId}")
    public Result<List<Baseline>> getByProject(@PathVariable Long projectId) {
        return Result.success(baselineService.getByProject(projectId));
    }

    @Operation(summary = "对比两个基线")
    @GetMapping("/compare")
    public Result<Map<String, Object>> compare(
            @RequestParam Long baselineId1,
            @RequestParam Long baselineId2) {
        return Result.success(baselineService.compare(baselineId1, baselineId2));
    }

    @Operation(summary = "基线双签锁定（v1.47 BUG #119 P0 修复）")
    @PostMapping("/{id}/lock")
    public Result<Baseline> lockBaseline(
            @PathVariable Long id,
            @RequestParam Long user1Id,
            @RequestParam Long signatureId1,
            @RequestParam Long user2Id,
            @RequestParam Long signatureId2) {
        return Result.success(baselineService.lockBaseline(id, user1Id, signatureId1, user2Id, signatureId2));
    }

    @Operation(summary = "基线双签解锁（v1.42 P1 修复：解锁同样需要双人电子签名）")
    @PostMapping("/{id}/unlock")
    public Result<Baseline> unlockBaseline(
            @PathVariable Long id,
            @RequestParam Long user1Id,
            @RequestParam Long signatureId1,
            @RequestParam Long user2Id,
            @RequestParam Long signatureId2,
            @RequestParam(required = false) String reason) {
        return Result.success(baselineService.unlockBaseline(id, user1Id, signatureId1, user2Id, signatureId2, reason));
    }

    @lombok.Data
    public static class CreateBaselineRequest {
        private Long projectId;
        private String name;
        private List<Long> requirementIds;
    }
}
