package com.zhutao.medrms.compliance.service;

import com.zhutao.medrms.change.mapper.ChangeRequestMapper;
import com.zhutao.medrms.compliance.domain.entity.AuditLog;
import com.zhutao.medrms.compliance.domain.entity.DhfEvidence;
import com.zhutao.medrms.compliance.mapper.AuditLogMapper;
import com.zhutao.medrms.esignature.domain.entity.ElectronicSignature;
import com.zhutao.medrms.esignature.mapper.ElectronicSignatureMapper;
import com.zhutao.medrms.project.domain.entity.Project;
import com.zhutao.medrms.project.mapper.ProjectMapper;
import com.zhutao.medrms.traceability.service.TraceabilityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DhfEvidenceService 单元测试（W2-D5）
 * 覆盖：DHF 证据包生成 / manifest 摘要 / 跨服务调用（mock）
 */
@ExtendWith(MockitoExtension.class)
class DhfEvidenceServiceTest {

    @Mock private ProjectMapper projectMapper;
    @Mock private TraceabilityService traceabilityService;
    @Mock private Iec62304ChecklistService iec62304ChecklistService;
    @Mock private ComplianceCheckService complianceCheckService;
    @Mock private ChangeRequestMapper changeRequestMapper;
    @Mock private AuditLogMapper auditLogMapper;
    @Mock private ElectronicSignatureMapper electronicSignatureMapper;

    @InjectMocks private DhfEvidenceService service;

    // ============================================================
    // 1. generateDhfPackage
    // ============================================================

    @Test
    @DisplayName("generateDhfPackage-项目不存在 → 未知项目")
    void generate_package_unknownProject() {
        when(projectMapper.selectById(99L)).thenReturn(null);
        when(traceabilityService.getTraceMatrix(any())).thenReturn(Collections.emptyList());
        when(traceabilityService.getCoverageStats(any())).thenReturn(Map.of("overall", 0));
        when(iec62304ChecklistService.getStats(any())).thenReturn(Collections.emptyMap());
        when(complianceCheckService.listEvidenceByProject(any())).thenReturn(Collections.emptyList());
        when(changeRequestMapper.selectList(null)).thenReturn(Collections.emptyList());
        when(auditLogMapper.selectList(null)).thenReturn(Collections.emptyList());
        when(electronicSignatureMapper.selectList(null)).thenReturn(Collections.emptyList());

        Map<String, Object> pkg = service.generateDhfPackage(99L);

        assertNotNull(pkg.get("packageId"));
        assertEquals(99L, pkg.get("projectId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> project = (Map<String, Object>) pkg.get("project");
        assertEquals("未知项目", project.get("projectName"));
    }

    @Test
    @DisplayName("generateDhfPackage-PASS 当 覆盖率≥90% 且 0 不合规")
    void generate_package_pass() {
        Project p = new Project();
        p.setId(1L);
        p.setProjectNo("P-001");
        p.setProjectName("ECG");
        p.setStatus("InProgress");
        p.setTemplateCode("TPL-001");
        when(projectMapper.selectById(1L)).thenReturn(p);

        when(traceabilityService.getTraceMatrix(1L)).thenReturn(Collections.emptyList());
        when(traceabilityService.getCoverageStats(1L)).thenReturn(Map.of("overall", 95));
        when(iec62304ChecklistService.getStats(1L)).thenReturn(Map.of(
            "compliant", 10, "nonCompliant", 0, "partial", 1, "notApplicable", 0, "pending", 0
        ));
        when(complianceCheckService.listEvidenceByProject(1L)).thenReturn(Collections.emptyList());
        when(changeRequestMapper.selectList(null)).thenReturn(Collections.emptyList());
        when(auditLogMapper.selectList(null)).thenReturn(Collections.emptyList());
        when(electronicSignatureMapper.selectList(null)).thenReturn(Collections.emptyList());

        Map<String, Object> pkg = service.generateDhfPackage(1L);

        assertEquals("PASS", pkg.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> verdict = (Map<String, Object>) pkg.get("verdict");
        assertEquals("PASS", verdict.get("status"));
    }

    @Test
    @DisplayName("generateDhfPackage-WARN 当 70%≤coverage<90% 且 0 不合规")
    void generate_package_warn() {
        when(projectMapper.selectById(1L)).thenReturn(null);
        when(traceabilityService.getTraceMatrix(1L)).thenReturn(Collections.emptyList());
        when(traceabilityService.getCoverageStats(1L)).thenReturn(Map.of("overall", 80));
        when(iec62304ChecklistService.getStats(1L)).thenReturn(Map.of(
            "compliant", 8, "nonCompliant", 0, "partial", 2, "notApplicable", 1, "pending", 1
        ));
        when(complianceCheckService.listEvidenceByProject(1L)).thenReturn(Collections.emptyList());
        when(changeRequestMapper.selectList(null)).thenReturn(Collections.emptyList());
        when(auditLogMapper.selectList(null)).thenReturn(Collections.emptyList());
        when(electronicSignatureMapper.selectList(null)).thenReturn(Collections.emptyList());

        Map<String, Object> pkg = service.generateDhfPackage(1L);

        assertEquals("WARN", pkg.get("status"));
    }

    @Test
    @DisplayName("generateDhfPackage-FAIL 当 coverage<70%")
    void generate_package_fail() {
        when(projectMapper.selectById(1L)).thenReturn(null);
        when(traceabilityService.getTraceMatrix(1L)).thenReturn(Collections.emptyList());
        when(traceabilityService.getCoverageStats(1L)).thenReturn(Map.of("overall", 50));
        when(iec62304ChecklistService.getStats(1L)).thenReturn(Map.of(
            "compliant", 5, "nonCompliant", 2, "partial", 3, "notApplicable", 0, "pending", 0
        ));
        when(complianceCheckService.listEvidenceByProject(1L)).thenReturn(Collections.emptyList());
        when(changeRequestMapper.selectList(null)).thenReturn(Collections.emptyList());
        when(auditLogMapper.selectList(null)).thenReturn(Collections.emptyList());
        when(electronicSignatureMapper.selectList(null)).thenReturn(Collections.emptyList());

        Map<String, Object> pkg = service.generateDhfPackage(1L);

        assertEquals("FAIL", pkg.get("status"));
    }

    @Test
    @DisplayName("generateDhfPackage-FAIL 当 nonCompliant>0 即使 100% 覆盖")
    void generate_package_failNonCompliant() {
        when(projectMapper.selectById(1L)).thenReturn(null);
        when(traceabilityService.getTraceMatrix(1L)).thenReturn(Collections.emptyList());
        when(traceabilityService.getCoverageStats(1L)).thenReturn(Map.of("overall", 100));
        when(iec62304ChecklistService.getStats(1L)).thenReturn(Map.of(
            "compliant", 11, "nonCompliant", 1, "partial", 0, "notApplicable", 0, "pending", 0
        ));
        when(complianceCheckService.listEvidenceByProject(1L)).thenReturn(Collections.emptyList());
        when(changeRequestMapper.selectList(null)).thenReturn(Collections.emptyList());
        when(auditLogMapper.selectList(null)).thenReturn(Collections.emptyList());
        when(electronicSignatureMapper.selectList(null)).thenReturn(Collections.emptyList());

        Map<String, Object> pkg = service.generateDhfPackage(1L);

        assertEquals("FAIL", pkg.get("status"));
    }

    @Test
    @DisplayName("generateDhfPackage-含证据/审计/签名/变更数据")
    void generate_package_fullData() {
        when(projectMapper.selectById(1L)).thenReturn(null);
        when(traceabilityService.getTraceMatrix(1L)).thenReturn(List.of(Map.of("urs", "x")));
        when(traceabilityService.getCoverageStats(1L)).thenReturn(Map.of("overall", 90));
        when(iec62304ChecklistService.getStats(1L)).thenReturn(Map.of(
            "compliant", 12, "nonCompliant", 0, "partial", 0, "notApplicable", 0, "pending", 0
        ));
        DhfEvidence ev = new DhfEvidence();
        ev.setId(1L);
        ev.setEvidenceName("spec.pdf");
        when(complianceCheckService.listEvidenceByProject(1L)).thenReturn(List.of(ev));
        when(changeRequestMapper.selectList(null)).thenReturn(Collections.emptyList());
        when(auditLogMapper.selectList(null)).thenReturn(Collections.emptyList());
        when(electronicSignatureMapper.selectList(null)).thenReturn(Collections.emptyList());

        Map<String, Object> pkg = service.generateDhfPackage(1L);

        assertEquals(1, pkg.get("dhfEvidenceCount"));
        assertEquals(1, pkg.get("traceMatrixSize"));
        assertEquals("PASS", pkg.get("status"));
    }

    // ============================================================
    // 2. getDhfManifest
    // ============================================================

    @Test
    @DisplayName("getDhfManifest-7 个 section 节点")
    void manifest() {
        when(projectMapper.selectById(1L)).thenReturn(null);

        Map<String, Object> m = service.getDhfManifest(1L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) m.get("sections");
        assertEquals(7, sections.size());
        assertTrue(sections.stream().anyMatch(s -> "traceMatrix".equals(s.get("key"))));
        assertTrue(sections.stream().anyMatch(s -> "auditLogs".equals(s.get("key"))));
    }
}
