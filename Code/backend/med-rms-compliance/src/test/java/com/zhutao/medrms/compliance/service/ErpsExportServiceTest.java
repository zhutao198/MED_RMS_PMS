package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.change.domain.entity.ChangeRequest;
import com.zhutao.medrms.change.mapper.ChangeRequestMapper;
import com.zhutao.medrms.compliance.domain.entity.ProblemReport;
import com.zhutao.medrms.compliance.domain.entity.SoupComponent;
import com.zhutao.medrms.compliance.mapper.ProblemReportMapper;
import com.zhutao.medrms.compliance.mapper.SoupComponentMapper;
import com.zhutao.medrms.project.domain.entity.Project;
import com.zhutao.medrms.project.mapper.ProjectMapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.zhutao.medrms.risk.domain.entity.RiskAssessment;
import com.zhutao.medrms.risk.mapper.RiskAssessmentMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * NMPA eRPS 报告导出单元测试（W8-D1 — FR-1.12 收尾测试）
 * 覆盖：项目不存在 / 完整导出（产品+需求+风险+变更+问题+SOUP+IEC 62304）
 */
@ExtendWith(MockitoExtension.class)
class ErpsExportServiceTest {

    @Mock private ProjectMapper projectMapper;
    @Mock private RequirementMapper requirementMapper;
    @Mock private RiskAssessmentMapper riskAssessmentMapper;
    @Mock private ChangeRequestMapper changeRequestMapper;
    @Mock private ProblemReportMapper problemReportMapper;
    @Mock private SoupComponentMapper soupComponentMapper;
    @Mock private Iec62304ChecklistService iec62304ChecklistService;

    @InjectMocks private ErpsExportService service;

    // ============================================================
    // 1. 异常路径
    // ============================================================

    @Test
    @DisplayName("exportProject-项目不存在返回 error")
    void exportProject_notFound() {
        when(projectMapper.selectById(99L)).thenReturn(null);

        Map<String, Object> result = service.exportProject(99L);

        assertEquals("项目不存在", result.get("error"));
        assertEquals(99L, result.get("projectId"));
    }

    // ============================================================
    // 2. 正常路径
    // ============================================================

    @Test
    @DisplayName("exportProject-完整导出（项目+7 大模块）")
    void exportProject_full() {
        Project p = new Project();
        p.setId(1L);
        p.setProjectNo("P-001");
        p.setProjectName("ECG Monitor");
        p.setStatus("InProgress");
        p.setTemplateCode("TPL-001");
        p.setDescription("Electrocardiogram monitor v3.0");
        when(projectMapper.selectById(1L)).thenReturn(p);

        // Mock 各模块数据为空（避免复杂 Stub）
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(riskAssessmentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(changeRequestMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(problemReportMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(soupComponentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(iec62304ChecklistService.getStats(1L)).thenReturn(Collections.emptyMap());

        Map<String, Object> result = service.exportProject(1L);

        // 顶层字段
        assertEquals("NMPA-eRPS-CHINA-MEDICAL-DEVICE-v1", result.get("schema"));
        assertNotNull(result.get("generatedAt"));
        assertEquals("Med-RMS PMS", result.get("generator"));

        // 7 大模块（按 Service 实际 key 名）
        assertNotNull(result.get("productInfo"));
        assertNotNull(result.get("softwareDescription"));
        assertNotNull(result.get("riskManagementSummary"));
        assertNotNull(result.get("requirementTrace"));
        assertNotNull(result.get("changeControl"));
        assertNotNull(result.get("problemReports"));
        assertNotNull(result.get("iec62304Summary"));
        assertNotNull(result.get("checksum"));
    }

    @Test
    @DisplayName("exportProject-productInfo 字段正确")
    void exportProject_productInfo() {
        Project p = new Project();
        p.setId(2L);
        p.setProjectNo("P-002");
        p.setProjectName("ECG v2");
        p.setStatus("InProgress");
        p.setDescription("Test product");
        when(projectMapper.selectById(2L)).thenReturn(p);
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(riskAssessmentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(changeRequestMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(problemReportMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(soupComponentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(iec62304ChecklistService.getStats(2L)).thenReturn(Collections.emptyMap());

        Map<String, Object> result = service.exportProject(2L);

        @SuppressWarnings("unchecked")
        Map<String, Object> productInfo = (Map<String, Object>) result.get("productInfo");
        assertEquals("P-002", productInfo.get("projectNo"));
        assertEquals("ECG v2", productInfo.get("projectName"));
        assertEquals("Test product", productInfo.get("description"));
    }

    @Test
    @DisplayName("exportProject-softwareDescription 字段含 requirementCount + safetyLevel")
    void exportProject_softwareDescription() {
        Project p = new Project();
        p.setId(3L);
        p.setProjectName("Test");
        p.setStatus("InProgress");
        when(projectMapper.selectById(3L)).thenReturn(p);
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(riskAssessmentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(changeRequestMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(problemReportMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(soupComponentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(iec62304ChecklistService.getStats(3L)).thenReturn(Collections.emptyMap());

        Map<String, Object> result = service.exportProject(3L);

        @SuppressWarnings("unchecked")
        Map<String, Object> sd = (Map<String, Object>) result.get("softwareDescription");
        assertNotNull(sd.get("softwareName"));
        assertNotNull(sd.get("versionNo"));
        assertNotNull(sd.get("safetyLevel"));  // A/B/C
        assertNotNull(sd.get("requirementCount"));
        assertNotNull(sd.get("soupList"));
    }
}
