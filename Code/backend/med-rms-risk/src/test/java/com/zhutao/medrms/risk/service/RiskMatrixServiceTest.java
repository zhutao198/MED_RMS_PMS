package com.zhutao.medrms.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.risk.domain.entity.RiskMatrix;
import com.zhutao.medrms.risk.mapper.RiskMatrixMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RiskMatrixService 单元测试（W2-D7）
 * 覆盖：列表/详情/RPN 计算（S*P*D）/风险等级阈值/风险区域/残余风险
 */
@ExtendWith(MockitoExtension.class)
class RiskMatrixServiceTest {

    @Mock private RiskMatrixMapper riskMatrixMapper;

    @InjectMocks private RiskMatrixService service;

    // ============================================================
    // 1. 列表
    // ============================================================

    @Test
    @DisplayName("listByProject-透传 mapper（按 RPN 倒序）")
    void listByProject() {
        when(riskMatrixMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(new RiskMatrix()));
        assertEquals(1, service.listByProject(1L).size());
    }

    @Test
    @DisplayName("listByType-按 matrixType 过滤")
    void listByType() {
        when(riskMatrixMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(new RiskMatrix()));
        assertEquals(1, service.listByType("FMEA").size());
    }

    // ============================================================
    // 2. getById
    // ============================================================

    @Test
    @DisplayName("getById-不存在抛 RS0201")
    void getById_notFound() {
        when(riskMatrixMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(99L));
        assertEquals("RS0201", ex.getCode());
    }

    @Test
    @DisplayName("getById-软删的也抛 RS0201")
    void getById_deleted() {
        RiskMatrix rm = new RiskMatrix();
        rm.setId(1L);
        rm.setIsDeleted(true);
        when(riskMatrixMapper.selectById(1L)).thenReturn(rm);

        assertThrows(BusinessException.class, () -> service.getById(1L));
    }

    // ============================================================
    // 3. create - RPN 计算
    // ============================================================

    @Test
    @DisplayName("create-低 RPN（1*1*1=1）→ LOW / GREEN")
    void create_lowRpn() {
        RiskMatrix rm = matrix(1, 1, 1);

        RiskMatrix result = service.create(rm);

        assertEquals(new BigDecimal(1), result.getRpn());
        assertEquals("LOW", result.getRiskLevel());
        assertEquals("GREEN", result.getRiskZone());
    }

    @Test
    @DisplayName("create-中 RPN（3*3*3=27）→ MEDIUM / YELLOW")
    void create_mediumRpn() {
        RiskMatrix rm = matrix(3, 3, 3);

        RiskMatrix result = service.create(rm);

        assertEquals(new BigDecimal(27), result.getRpn());
        assertEquals("MEDIUM", result.getRiskLevel());
        assertEquals("YELLOW", result.getRiskZone());
    }

    @Test
    @DisplayName("create-高 RPN（5*5*5=125）→ HIGH / RED")
    void create_highRpn() {
        RiskMatrix rm = matrix(5, 5, 5);

        RiskMatrix result = service.create(rm);

        assertEquals(new BigDecimal(125), result.getRpn());
        assertEquals("HIGH", result.getRiskLevel());
        assertEquals("RED", result.getRiskZone());
    }

    // ============================================================
    // 4. update - 部分字段更新
    // ============================================================

    @Test
    @DisplayName("update-只更新 severity + 重新计算 RPN")
    void update_partial() {
        RiskMatrix existing = matrix(3, 3, 3);
        existing.setId(1L);
        existing.setRpn(new BigDecimal(27));
        existing.setRiskLevel("MEDIUM");
        when(riskMatrixMapper.selectById(1L)).thenReturn(existing);

        RiskMatrix patch = new RiskMatrix();
        patch.setSeverity("5");

        RiskMatrix result = service.update(1L, patch);

        assertEquals("5", result.getSeverity());
        assertEquals(new BigDecimal(45), result.getRpn()); // 5*3*3
        assertEquals("MEDIUM", result.getRiskLevel()); // 45 < 50
    }

    // ============================================================
    // 5. calculateResidual
    // ============================================================

    @Test
    @DisplayName("calculateResidual-残余 RPN = 原 RPN/2")
    void calculateResidual_highToMedium() {
        RiskMatrix existing = matrix(5, 5, 5);
        existing.setId(1L);
        when(riskMatrixMapper.selectById(1L)).thenReturn(existing);

        RiskMatrix result = service.calculateResidual(1L, "增加审核");

        // 125 / 2 = 62
        assertEquals(new BigDecimal(62), result.getResidualRpn());
        assertEquals("HIGH", result.getResidualRisk());
    }

    @Test
    @DisplayName("calculateResidual-中等风险 mitigation → LOW")
    void calculateResidual_toLow() {
        RiskMatrix existing = matrix(3, 3, 3);
        existing.setId(1L);
        when(riskMatrixMapper.selectById(1L)).thenReturn(existing);

        RiskMatrix result = service.calculateResidual(1L, "mitigation");

        // 27 / 2 = 13
        assertEquals(new BigDecimal(13), result.getResidualRpn());
        assertEquals("LOW", result.getResidualRisk());
    }

    private RiskMatrix matrix(int s, int p, int d) {
        RiskMatrix rm = new RiskMatrix();
        rm.setSeverity(String.valueOf(s));
        rm.setProbability(String.valueOf(p));
        rm.setDetectability(String.valueOf(d));
        return rm;
    }
}
