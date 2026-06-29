package com.zhutao.medrms.risk.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.risk.domain.entity.RiskAssessment;
import com.zhutao.medrms.risk.service.RiskAssessmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * RiskController 单元测试（v1.27 R28）
 * 覆盖风险评估/FMEA/控制措施/项目风险报告
 */
@ExtendWith(MockitoExtension.class)
class RiskControllerTest {

    @Mock
    private RiskAssessmentService riskAssessmentService;

    @InjectMocks
    private RiskController controller;

    @Test
    void assess_returnsRiskAssessment() {
        RiskController.RiskAssessRequest req = new RiskController.RiskAssessRequest();
        req.setRequirementId(1L);
        req.setRiskLevel("HIGH");
        req.setHazardLevel("S2");
        req.setAssessedBy(10L);

        RiskAssessment saved = new RiskAssessment();
        saved.setId(99L);
        saved.setRiskLevel("HIGH");
        when(riskAssessmentService.assess(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(saved);

        Result<RiskAssessment> result = controller.assess(req);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(99L, result.getData().getId());
        assertEquals("HIGH", result.getData().getRiskLevel());
    }

    @Test
    void getByRequirement_returnsList() {
        RiskAssessment ra = new RiskAssessment();
        ra.setId(1L);
        when(riskAssessmentService.getByRequirement(1L)).thenReturn(List.of(ra));

        Result<List<RiskAssessment>> result = controller.getByRequirement(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    void getRiskReport_returnsMap() {
        Map<String, Object> report = new HashMap<>();
        report.put("totalRisks", 10);
        report.put("highRisks", 2);
        when(riskAssessmentService.getRiskReport(1L)).thenReturn(report);

        Result<Map<String, Object>> result = controller.getRiskReport(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(10, result.getData().get("totalRisks"));
        assertEquals(2, result.getData().get("highRisks"));
    }

    @Test
    void saveFmea_calculatesRpn() {
        RiskController.FmeaRequest req = new RiskController.FmeaRequest();
        req.setSeverity(7);
        req.setOccurrence(3);
        req.setDetection(5);
        req.setActionPlan("增加单元测试");

        RiskAssessment saved = new RiskAssessment();
        saved.setId(1L);
        saved.setRpn(105);
        when(riskAssessmentService.saveFmea(any(), anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(saved);

        Result<RiskAssessment> result = controller.saveFmea(1L, req);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(105, result.getData().getRpn());
    }

    @Test
    void listFmea_sortedByRpnDesc() {
        when(riskAssessmentService.listFmea(any())).thenReturn(List.of(new RiskAssessment(), new RiskAssessment()));

        Result<List<RiskAssessment>> result = controller.listFmea(50);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().size());
    }

    @Test
    void updateActionStatus_returnsUpdated() {
        RiskAssessment updated = new RiskAssessment();
        updated.setId(1L);
        updated.setActionStatus("COMPLETED");
        when(riskAssessmentService.updateActionStatus(eq(1L), eq("COMPLETED"))).thenReturn(updated);

        Result<RiskAssessment> result = controller.updateActionStatus(1L, "COMPLETED");

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("COMPLETED", result.getData().getActionStatus());
    }
}
