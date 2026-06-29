package com.zhutao.medrms.risk.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.risk.domain.entity.RiskAssessment;
import com.zhutao.medrms.risk.service.RiskAssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "风险管理", description = "风险识别、评估、控制措施接口")
@RestController
@RequestMapping("/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskAssessmentService riskAssessmentService;

    @Operation(summary = "风险评估")
    @PostMapping("/assess")
    public Result<RiskAssessment> assess(@RequestBody RiskAssessRequest request) {
        return Result.success(riskAssessmentService.assess(
                request.getRequirementId(),
                request.getRiskLevel(),
                request.getHazardLevel(),
                request.getHazardSource(),
                request.getHazardSituation(),
                request.getHarm(),
                request.getControlMeasure(),
                request.getAssessedBy()
        ));
    }

    @Operation(summary = "获取需求的风险评估列表")
    @GetMapping("/requirement/{requirementId}")
    public Result<List<RiskAssessment>> getByRequirement(@PathVariable Long requirementId) {
        return Result.success(riskAssessmentService.getByRequirement(requirementId));
    }

    @Operation(summary = "获取项目风险报告")
    @GetMapping("/report/{projectId}")
    public Result<Map<String, Object>> getRiskReport(@PathVariable Long projectId) {
        return Result.success(riskAssessmentService.getRiskReport(projectId));
    }

    @Operation(summary = "更新控制措施")
    @PutMapping("/{id}/control")
    public Result<RiskAssessment> updateControl(
            @PathVariable Long id,
            @RequestParam String controlMeasure,
            @RequestParam Long reviewedBy) {
        return Result.success(riskAssessmentService.updateControlMeasure(id, controlMeasure, reviewedBy));
    }

    @lombok.Data
    public static class RiskAssessRequest {
        private Long requirementId;
        private String riskLevel;
        private String hazardLevel;
        private String hazardSource;
        private String hazardSituation;
        private String harm;
        private String controlMeasure;
        private Long assessedBy;
    }

    // ========== FR-1.8 FMEA 在线编辑器 ==========

    @Operation(summary = "FMEA 编辑：保存 S/O/D 与改进措施（自动计算 RPN）")
    @PostMapping("/{id}/fmea")
    public Result<RiskAssessment> saveFmea(@PathVariable Long id, @RequestBody FmeaRequest req) {
        return Result.success(riskAssessmentService.saveFmea(id, req.getSeverity(), req.getOccurrence(),
                req.getDetection(), req.getActionPlan(), req.getActionOwner(), req.getActionDueDate()));
    }

    @Operation(summary = "更新改进措施状态")
    @PutMapping("/{id}/action-status")
    public Result<RiskAssessment> updateActionStatus(@PathVariable Long id, @RequestParam String actionStatus) {
        return Result.success(riskAssessmentService.updateActionStatus(id, actionStatus));
    }

    @Operation(summary = "FMEA 列表（按 RPN 降序）")
    @GetMapping("/fmea")
    public Result<List<RiskAssessment>> listFmea(@RequestParam(required = false) Integer rpnThreshold) {
        return Result.success(riskAssessmentService.listFmea(rpnThreshold));
    }

    @Data
    public static class FmeaRequest {
        private Integer severity;
        private Integer occurrence;
        private Integer detection;
        private String actionPlan;
        private String actionOwner;
        private LocalDateTime actionDueDate;
    }
}