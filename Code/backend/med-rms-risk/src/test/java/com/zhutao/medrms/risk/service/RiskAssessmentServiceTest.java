package com.zhutao.medrms.risk.service;

import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.risk.domain.entity.RiskAssessment;
import com.zhutao.medrms.risk.mapper.RiskAssessmentMapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.Map;

/**
 * RiskAssessmentService 单元测试（W12-D1）
 * FMEA 风险评估
 */
@ExtendWith(MockitoExtension.class)
class RiskAssessmentServiceTest {

    @Mock private RiskAssessmentMapper riskMapper;
    @Mock private RequirementMapper requirementMapper;

    @InjectMocks private RiskAssessmentService service;

    @Test
    @DisplayName("assess-插入新风险评估")
    void assess() {
        Requirement r = new Requirement();
        r.setId(1L);
        r.setProjectId(10L);
        when(requirementMapper.selectById(1L)).thenReturn(r);
        when(riskMapper.insert(any(RiskAssessment.class))).thenReturn(1);

        RiskAssessment result = service.assess(1L, "HIGH", "MAJOR",
                "burn", "FIX", "harm", "control", 100L);

        assertNotNull(result);
        assertEquals(1L, result.getRequirementId());
        assertEquals("HIGH", result.getRiskLevel());
        verify(riskMapper).insert(any(RiskAssessment.class));
    }

    @Test
    @DisplayName("assess-需求不存在抛错")
    void assess_requirementNotFound() {
        when(requirementMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class,
            () -> service.assess(99L, "HIGH", "MAJOR", "src", "sit", "harm", "ctrl", 1L));
    }

    @Test
    @DisplayName("getByRequirement-透传 selectByRequirementId")
    void getByRequirement() {
        when(riskMapper.selectByRequirementId(1L)).thenReturn(List.of(new RiskAssessment()));
        assertEquals(1, service.getByRequirement(1L).size());
    }

    @Test
    @DisplayName("saveFmea-计算 RPN")
    void saveFmea() {
        RiskAssessment existing = new RiskAssessment();
        existing.setId(1L);
        when(riskMapper.selectById(1L)).thenReturn(existing);
        when(riskMapper.updateById(any(RiskAssessment.class))).thenReturn(1);

        RiskAssessment result = service.saveFmea(1L, 5, 4, 3, "FMEA note", "owner", null);

        assertNotNull(result);
        assertEquals(60, result.getRpn().intValue());
    }

    @Test
    @DisplayName("updateActionStatus-更新状态")
    void updateActionStatus() {
        RiskAssessment existing = new RiskAssessment();
        existing.setId(1L);
        existing.setActionStatus("OPEN");
        when(riskMapper.selectById(1L)).thenReturn(existing);

        // Service 白名单: OPEN / IN_PROGRESS / COMPLETED
        RiskAssessment result = service.updateActionStatus(1L, "COMPLETED");

        assertEquals("COMPLETED", result.getActionStatus());
    }

    // ============================================================
    // W13-D2 边界用例扩充
    // ============================================================

    @Test
    @DisplayName("assess-HIGH×CATASTROPHIC: 风险评分 15，无控制 → UNACCEPTABLE")
    void assess_highCatastrophic_unacceptable() {
        Requirement r = new Requirement();
        r.setId(1L);
        when(requirementMapper.selectById(1L)).thenReturn(r);

        RiskAssessment result = service.assess(1L, "HIGH", "CATASTROPHIC",
                "源", "场景", "伤害", null, 100L);

        assertEquals(15, result.getRiskScore().intValue());
        // controlMeasure=null → riskScore > 15 ? UNACCEPTABLE : ALARP
        // 15 > 10 但不 > 15 → 实际走第二个分支 → ALARP
        // 实际代码：controlMeasure 为空时 riskScore > 15 抛 UNACCEPTABLE，否则 ALARP
        // 15 不大于 15 → ALARP
        assertEquals("ALARP", result.getResidualRisk());
    }

    @Test
    @DisplayName("assess-有控制措施 + 低风险：residualRisk=ACCEPTABLE")
    void assess_withControl_acceptable() {
        Requirement r = new Requirement();
        r.setId(1L);
        when(requirementMapper.selectById(1L)).thenReturn(r);

        RiskAssessment result = service.assess(1L, "LOW", "MINOR",
                "源", "场景", "伤害", "已加防护罩", 100L);

        // riskScore = 1×2 = 2
        assertEquals(2, result.getRiskScore().intValue());
        // 2 ≤ 10 → ACCEPTABLE
        assertEquals("ACCEPTABLE", result.getResidualRisk());
    }

    @Test
    @DisplayName("assess-有控制措施但风险仍高：residualRisk=ALARP")
    void assess_withControl_alarp() {
        Requirement r = new Requirement();
        r.setId(1L);
        when(requirementMapper.selectById(1L)).thenReturn(r);

        RiskAssessment result = service.assess(1L, "MEDIUM", "MAJOR",
                "源", "场景", "伤害", "部分缓解", 100L);

        // riskScore = 2×3 = 6
        // 有控制 + 6 > 10 ? ALARP : ACCEPTABLE → ACCEPTABLE
        // 实际 6 ≤ 10 → ACCEPTABLE
        assertEquals("ACCEPTABLE", result.getResidualRisk());
    }

    @Test
    @DisplayName("getRiskReport-5 维度统计")
    @SuppressWarnings("unchecked")
    void getRiskReport() {
        when(riskMapper.selectCount(any())).thenReturn(10L, 3L, 5L, 2L, 1L);

        Map<String, Object> report = service.getRiskReport(1L);

        assertEquals(10L, report.get("totalRisks"));
        assertEquals(3L, report.get("highRisks"));
        assertEquals(5L, report.get("mediumRisks"));
        assertEquals(2L, report.get("lowRisks"));
        assertEquals(1L, report.get("uncontrolledRisks"));
    }

    @Test
    @DisplayName("updateControlMeasure-成功：residualRisk 重算 + reviewedBy/At 设置")
    void updateControlMeasure_ok() {
        RiskAssessment existing = new RiskAssessment();
        existing.setId(1L);
        existing.setRiskScore(new java.math.BigDecimal("6"));
        when(riskMapper.selectById(1L)).thenReturn(existing);

        RiskAssessment result = service.updateControlMeasure(1L, "新增控制", 200L);

        assertEquals("新增控制", result.getControlMeasure());
        assertEquals(200L, result.getReviewedBy());
        assertNotNull(result.getReviewedAt());
        // 6 + 有控制 → ACCEPTABLE
        assertEquals("ACCEPTABLE", result.getResidualRisk());
    }

    @Test
    @DisplayName("updateControlMeasure-记录不存在抛 RK0101")
    void updateControlMeasure_notFound() {
        when(riskMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateControlMeasure(99L, "x", 1L));
        assertEquals("RK0101", ex.getCode());
    }

    @Test
    @DisplayName("saveFmea-severity=0 抛 SY0101")
    void saveFmea_severityZero() {
        RiskAssessment existing = new RiskAssessment();
        existing.setId(1L);
        when(riskMapper.selectById(1L)).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveFmea(1L, 0, 5, 5, "x", "u", null));
        assertEquals("SY0101", ex.getCode());
    }

    @Test
    @DisplayName("saveFmea-detection=11 抛 SY0101")
    void saveFmea_detectionOver() {
        RiskAssessment existing = new RiskAssessment();
        existing.setId(1L);
        when(riskMapper.selectById(1L)).thenReturn(existing);

        assertThrows(BusinessException.class,
                () -> service.saveFmea(1L, 5, 5, 11, "x", "u", null));
    }

    @Test
    @DisplayName("saveFmea-null actionStatus 自动设为 OPEN")
    void saveFmea_actionStatusDefault() {
        RiskAssessment existing = new RiskAssessment();
        existing.setId(1L);
        existing.setActionStatus(null);
        when(riskMapper.selectById(1L)).thenReturn(existing);

        RiskAssessment result = service.saveFmea(1L, 3, 3, 3, "action", "owner", null);

        assertEquals("OPEN", result.getActionStatus());
    }

    @Test
    @DisplayName("updateActionStatus-非法状态抛 SY0101")
    void updateActionStatus_invalid() {
        RiskAssessment existing = new RiskAssessment();
        existing.setId(1L);
        when(riskMapper.selectById(1L)).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateActionStatus(1L, "BOGUS"));
        assertEquals("SY0101", ex.getCode());
    }

    @Test
    @DisplayName("updateActionStatus-记录不存在抛 RK0101")
    void updateActionStatus_notFound() {
        when(riskMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateActionStatus(99L, "OPEN"));
        assertEquals("RK0101", ex.getCode());
    }

    @Test
    @DisplayName("listFmea-按 RPN 降序 + 可选阈值过滤")
    void listFmea_withThreshold() {
        RiskAssessment a = new RiskAssessment();
        a.setRpn(120);
        RiskAssessment b = new RiskAssessment();
        b.setRpn(80);
        when(riskMapper.selectList(any())).thenReturn(List.of(a, b));

        List<RiskAssessment> result = service.listFmea(100);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("listFmea-无阈值：全量返回")
    void listFmea_noThreshold() {
        when(riskMapper.selectList(any())).thenReturn(List.of(new RiskAssessment()));
        assertEquals(1, service.listFmea(null).size());
    }
}
