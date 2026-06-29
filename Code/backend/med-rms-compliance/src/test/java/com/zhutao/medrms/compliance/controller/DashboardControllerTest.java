package com.zhutao.medrms.compliance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.change.mapper.ChangeRequestMapper;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.compliance.domain.entity.DashboardConfig;
import com.zhutao.medrms.compliance.mapper.ProblemReportMapper;
import com.zhutao.medrms.compliance.service.DashboardConfigService;
import com.zhutao.medrms.compliance.service.DhfEvidenceService;
import com.zhutao.medrms.compliance.service.Iec62304ChecklistService;
import com.zhutao.medrms.compliance.service.StatisticsService;
import com.zhutao.medrms.project.mapper.ProjectMapper;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.zhutao.medrms.risk.mapper.RiskAssessmentMapper;
import com.zhutao.medrms.traceability.service.TraceabilityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * DashboardController 单元测试（W14-D1）
 * FR-1.2 多视角工作视图 Controller
 */
@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock private RequirementMapper requirementMapper;
    @Mock private RiskAssessmentMapper riskAssessmentMapper;
    @Mock private ChangeRequestMapper changeRequestMapper;
    @Mock private ProblemReportMapper problemReportMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private TraceabilityService traceabilityService;
    @Mock private Iec62304ChecklistService iec62304ChecklistService;
    @Mock private DhfEvidenceService dhfEvidenceService;
    @Mock private StatisticsService statisticsService;
    @Mock private DashboardConfigService dashboardConfigService;

    @InjectMocks private DashboardController controller;

    @Test
    @DisplayName("getDashboard-统一仪表盘：5 维度统计 + 用户布局")
    @SuppressWarnings("unchecked")
    void getDashboard() {
        when(statisticsService.getRequirementStats(any())).thenReturn(Map.of("total", 10));
        when(statisticsService.getRiskStats(any())).thenReturn(Map.of("high", 2));
        when(statisticsService.getChangeStats(any())).thenReturn(Map.of("open", 3));
        when(statisticsService.getComplianceStats(any())).thenReturn(Map.of("issues", 1));
        when(statisticsService.getTrends(any())).thenReturn(Map.of());
        when(dashboardConfigService.getCurrentUserLayout()).thenReturn(new DashboardConfig());

        Result<Map<String, Object>> result = controller.getDashboard(1L);

        assertEquals(200, result.getCode());
        Map<String, Object> view = result.getData();
        assertNotNull(view.get("requirements"));
        assertNotNull(view.get("risk"));
        assertNotNull(view.get("change"));
        assertNotNull(view.get("compliance"));
        assertNotNull(view.get("trends"));
        assertNotNull(view.get("layout"));
    }

    @Test
    @DisplayName("getDashboard-布局服务异常：layout 字段为 null 但不抛错")
    @SuppressWarnings("unchecked")
    void getDashboard_layoutFailure() {
        when(statisticsService.getRequirementStats(any())).thenReturn(Map.of());
        when(statisticsService.getRiskStats(any())).thenReturn(Map.of());
        when(statisticsService.getChangeStats(any())).thenReturn(Map.of());
        when(statisticsService.getComplianceStats(any())).thenReturn(Map.of());
        when(statisticsService.getTrends(any())).thenReturn(Map.of());
        when(dashboardConfigService.getCurrentUserLayout())
                .thenThrow(new RuntimeException("layout service down"));

        Result<Map<String, Object>> result = controller.getDashboard(null);

        assertEquals(200, result.getCode());
        Map<String, Object> view = result.getData();
        assertNull(view.get("layout"), "布局服务异常时 layout 应兜底为 null");
    }

    @Test
    @DisplayName("saveLayout-保存成功")
    void saveLayout() {
        DashboardConfig input = new DashboardConfig();
        when(dashboardConfigService.saveLayout(any())).thenReturn(input);

        Result<DashboardConfig> result = controller.saveLayout(input);

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("resetLayout-恢复默认布局")
    void resetLayout() {
        DashboardConfig def = new DashboardConfig();
        def.setIsDefault(true);
        when(dashboardConfigService.resetToDefault()).thenReturn(def);

        Result<DashboardConfig> result = controller.resetLayout();

        assertTrue(result.getData().getIsDefault());
    }

    @Test
    @DisplayName("requirementsView-按状态/类型聚合 + 覆盖统计")
    @SuppressWarnings("unchecked")
    void requirementsView() {
        com.zhutao.medrms.requirement.domain.entity.Requirement r = new com.zhutao.medrms.requirement.domain.entity.Requirement();
        r.setStatus("DRAFT");
        r.setRequirementType("URS");
        r.setIsSuspect(true);
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(r));
        when(traceabilityService.getCoverageStats(any())).thenReturn(Map.of("rate", 85));

        Result<Map<String, Object>> result = controller.requirementsView(1L);

        Map<String, Object> view = result.getData();
        assertEquals(1, view.get("total"));
        assertEquals(1L, view.get("suspectCount"));
        assertNotNull(view.get("byStatus"));
        assertNotNull(view.get("byType"));
        assertNotNull(view.get("coverage"));
    }

    @Test
    @DisplayName("riskView-按风险等级/状态聚合 + avgRpn")
    @SuppressWarnings("unchecked")
    void riskView() {
        com.zhutao.medrms.risk.domain.entity.RiskAssessment a = new com.zhutao.medrms.risk.domain.entity.RiskAssessment();
        a.setRiskLevel("HIGH");
        a.setRiskStatus("OPEN");
        a.setRpn(120);
        when(riskAssessmentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(a));
        // projectId 为 null 时 Service 跳过 requirementMapper 过滤，无需 stub

        Result<Map<String, Object>> result = controller.riskView(null);

        Map<String, Object> view = result.getData();
        assertEquals(1, view.get("total"));
        assertEquals(1L, view.get("highCount"));
        assertEquals(120L, view.get("avgRpn"));
    }

    @Test
    @DisplayName("managementView-项目数 + 告警统计")
    @SuppressWarnings("unchecked")
    void managementView() {
        com.zhutao.medrms.project.domain.entity.Project p = new com.zhutao.medrms.project.domain.entity.Project();
        p.setStatus("IN_PROGRESS");
        when(projectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p));
        // suspectCount=1, pendingReviewCount=2, highRiskCount=2 → total=5
        when(requirementMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(1L, 2L);
        when(riskAssessmentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);
        when(traceabilityService.getCoverageStats(any())).thenReturn(Map.of("rate", 80));

        Result<Map<String, Object>> result = controller.managementView(1L);

        Map<String, Object> view = result.getData();
        assertEquals(1, view.get("projectCount"));
        Map<String, Object> alerts = (Map<String, Object>) view.get("alerts");
        assertEquals(5L, alerts.get("total"));
    }

    @Test
    @DisplayName("complianceView-IEC62304 + 变更/问题统计 + 项目数")
    @SuppressWarnings("unchecked")
    void complianceView() {
        when(iec62304ChecklistService.getStats(anyLong())).thenReturn(Map.of("checked", 10));
        when(changeRequestMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(problemReportMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        // Service 调 selectCount(null) 不是 any()，严格模式需要 isNull()
        when(projectMapper.selectCount(isNull())).thenReturn(5L);

        Result<Map<String, Object>> result = controller.complianceView(1L);

        Map<String, Object> view = result.getData();
        assertNotNull(view.get("iec62304"));
        assertNotNull(view.get("changes"));
        assertNotNull(view.get("problems"));
        assertEquals(5L, view.get("projectCount"));
    }
}
