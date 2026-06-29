package com.zhutao.medrms.compliance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.change.domain.entity.ChangeRequest;
import com.zhutao.medrms.change.mapper.ChangeRequestMapper;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.compliance.domain.entity.DashboardConfig;
import com.zhutao.medrms.compliance.domain.entity.ProblemReport;
import com.zhutao.medrms.compliance.mapper.ProblemReportMapper;
import com.zhutao.medrms.compliance.service.DashboardConfigService;
import com.zhutao.medrms.compliance.service.DhfEvidenceService;
import com.zhutao.medrms.compliance.service.Iec62304ChecklistService;
import com.zhutao.medrms.compliance.service.StatisticsService;
import com.zhutao.medrms.project.domain.entity.Project;
import com.zhutao.medrms.project.mapper.ProjectMapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.zhutao.medrms.risk.domain.entity.RiskAssessment;
import com.zhutao.medrms.risk.mapper.RiskAssessmentMapper;
import com.zhutao.medrms.traceability.service.TraceabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 多视角工作视图 - FR-1.2
 * 提供需求/风险/合规/管理四个视角的聚合数据，前端按 tab 切换展示。
 * <p>设计文档: 支撑域与通用域-详细设计.md §3.2 DashboardController</p>
 */
@Tag(name = "仪表盘", description = "多视角聚合数据 + 布局配置")
@Slf4j
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final RequirementMapper requirementMapper;
    private final RiskAssessmentMapper riskAssessmentMapper;
    private final ChangeRequestMapper changeRequestMapper;
    private final ProblemReportMapper problemReportMapper;
    private final ProjectMapper projectMapper;
    private final TraceabilityService traceabilityService;
    private final Iec62304ChecklistService iec62304ChecklistService;
    private final DhfEvidenceService dhfEvidenceService;
    private final StatisticsService statisticsService;
    private final DashboardConfigService dashboardConfigService;

    @Operation(summary = "统一仪表盘（聚合多视角 + 用户布局）")
    @GetMapping
    public Result<Map<String, Object>> getDashboard(@RequestParam(required = false) Long projectId) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("requirements", statisticsService.getRequirementStats(projectId));
        view.put("risk", statisticsService.getRiskStats(projectId));
        view.put("change", statisticsService.getChangeStats(projectId));
        view.put("compliance", statisticsService.getComplianceStats(projectId));
        view.put("trends", statisticsService.getTrends(projectId));
        try {
            view.put("layout", dashboardConfigService.getCurrentUserLayout());
        } catch (Exception e) {
            view.put("layout", null);
        }
        return Result.success(view);
    }

    @Operation(summary = "保存用户仪表盘布局")
    @PostMapping("/layout")
    public Result<DashboardConfig> saveLayout(@RequestBody DashboardConfig config) {
        try {
            return Result.success(dashboardConfigService.saveLayout(config));
        } catch (Exception e) {
            log.error("saveLayout error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Operation(summary = "恢复默认布局")
    @PostMapping("/layout/reset")
    public Result<DashboardConfig> resetLayout() {
        return Result.success(dashboardConfigService.resetToDefault());
    }

    @Operation(summary = "需求视角统计")
    @GetMapping("/view/requirements")
    public Result<Map<String, Object>> requirementsView(@RequestParam(required = false) Long projectId) {
        Map<String, Object> view = new LinkedHashMap<>();
        LambdaQueryWrapper<Requirement> rw = new LambdaQueryWrapper<>();
        rw.eq(Requirement::getIsDeleted, false);
        if (projectId != null) rw.eq(Requirement::getProjectId, projectId);
        List<Requirement> all = requirementMapper.selectList(rw);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        Map<String, Long> byType = new LinkedHashMap<>();
        long suspectCount = 0;
        for (Requirement r : all) {
            byStatus.merge(r.getStatus() == null ? "Draft" : r.getStatus(), 1L, Long::sum);
            byType.merge(r.getRequirementType() == null ? "OTHER" : r.getRequirementType(), 1L, Long::sum);
            if (Boolean.TRUE.equals(r.getIsSuspect())) suspectCount++;
        }
        view.put("total", all.size());
        view.put("byStatus", byStatus);
        view.put("byType", byType);
        view.put("suspectCount", suspectCount);
        view.put("coverage", traceabilityService.getCoverageStats(projectId));
        return Result.success(view);
    }

    @Operation(summary = "风险视角统计")
    @GetMapping("/view/risk")
    public Result<Map<String, Object>> riskView(@RequestParam(required = false) Long projectId) {
        Map<String, Object> view = new LinkedHashMap<>();
        LambdaQueryWrapper<RiskAssessment> rw = new LambdaQueryWrapper<>();
        rw.eq(RiskAssessment::getIsDeleted, false);
        List<RiskAssessment> all = riskAssessmentMapper.selectList(rw);
        if (projectId != null) {
            LambdaQueryWrapper<Requirement> reqW = new LambdaQueryWrapper<>();
            reqW.eq(Requirement::getProjectId, projectId).eq(Requirement::getIsDeleted, false);
            Set<Long> reqIds = new HashSet<>();
            requirementMapper.selectList(reqW).forEach(r -> reqIds.add(r.getId()));
            all = all.stream().filter(a -> reqIds.contains(a.getRequirementId())).toList();
        }
        Map<String, Long> byLevel = new LinkedHashMap<>();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long totalScore = 0;
        long highCount = 0;
        for (RiskAssessment a : all) {
            byLevel.merge(a.getRiskLevel() == null ? "UNKNOWN" : a.getRiskLevel(), 1L, Long::sum);
            byStatus.merge(a.getRiskStatus() == null ? "OPEN" : a.getRiskStatus(), 1L, Long::sum);
            if (a.getRpn() != null) totalScore += a.getRpn().longValue();
            if ("HIGH".equals(a.getRiskLevel())) highCount++;
        }
        view.put("total", all.size());
        view.put("highCount", highCount);
        view.put("avgRpn", all.isEmpty() ? 0 : totalScore / all.size());
        view.put("byLevel", byLevel);
        view.put("byStatus", byStatus);
        return Result.success(view);
    }

    @Operation(summary = "管理视角统计 (FR-2.10)")
    @GetMapping("/view/management")
    public Result<Map<String, Object>> managementView(@RequestParam(required = false) Long projectId) {
        Map<String, Object> view = new LinkedHashMap<>();
        LambdaQueryWrapper<Project> pw = new LambdaQueryWrapper<>();
        if (projectId != null) pw.eq(Project::getId, projectId);
        List<Project> projects = projectMapper.selectList(pw);
        Map<String, Long> projectByStatus = new LinkedHashMap<>();
        for (Project p : projects) {
            String s = p.getStatus() == null ? "PLANNING" : p.getStatus();
            projectByStatus.merge(s, 1L, Long::sum);
        }
        view.put("projectCount", projects.size());
        view.put("byStatus", projectByStatus);
        LambdaQueryWrapper<Requirement> suspectW = new LambdaQueryWrapper<>();
        suspectW.eq(Requirement::getIsSuspect, true).eq(Requirement::getIsDeleted, false);
        if (projectId != null) suspectW.eq(Requirement::getProjectId, projectId);
        long suspectCount = requirementMapper.selectCount(suspectW);

        LambdaQueryWrapper<Requirement> reviewW = new LambdaQueryWrapper<>();
        reviewW.eq(Requirement::getStatus, "PendingReview").eq(Requirement::getIsDeleted, false);
        if (projectId != null) reviewW.eq(Requirement::getProjectId, projectId);
        long pendingReviewCount = requirementMapper.selectCount(reviewW);

        LambdaQueryWrapper<RiskAssessment> riskW = new LambdaQueryWrapper<>();
        riskW.eq(RiskAssessment::getRiskLevel, "HIGH").eq(RiskAssessment::getIsDeleted, false);
        long highRiskCount = riskAssessmentMapper.selectCount(riskW);

        Map<String, Object> alerts = new LinkedHashMap<>();
        alerts.put("suspectCount", suspectCount);
        alerts.put("pendingReviewCount", pendingReviewCount);
        alerts.put("highRiskCount", highRiskCount);
        alerts.put("total", suspectCount + pendingReviewCount + highRiskCount);
        view.put("alerts", alerts);
        view.put("coverage", traceabilityService.getCoverageStats(projectId));
        return Result.success(view);
    }

    @Operation(summary = "合规视角统计")
    @GetMapping("/view/compliance")
    public Result<Map<String, Object>> complianceView(@RequestParam(required = false) Long projectId) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("iec62304", iec62304ChecklistService.getStats(projectId));
        LambdaQueryWrapper<ChangeRequest> cw = new LambdaQueryWrapper<>();
        cw.eq(ChangeRequest::getIsDeleted, false);
        List<ChangeRequest> changes = changeRequestMapper.selectList(cw);
        Map<String, Long> changeByStatus = new LinkedHashMap<>();
        for (ChangeRequest c : changes) {
            changeByStatus.merge(c.getStatus() == null ? "DRAFT" : c.getStatus(), 1L, Long::sum);
        }
        view.put("changes", Map.of("total", changes.size(), "byStatus", changeByStatus));
        LambdaQueryWrapper<ProblemReport> pw = new LambdaQueryWrapper<>();
        pw.eq(ProblemReport::getIsDeleted, false);
        List<ProblemReport> problems = problemReportMapper.selectList(pw);
        Map<String, Long> probBySeverity = new LinkedHashMap<>();
        for (ProblemReport p : problems) {
            probBySeverity.merge(p.getSeverity() == null ? "UNKNOWN" : p.getSeverity(), 1L, Long::sum);
        }
        view.put("problems", Map.of("total", problems.size(), "bySeverity", probBySeverity));
        long projectCount = projectMapper.selectCount(null);
        view.put("projectCount", projectCount);
        return Result.success(view);
    }
}
