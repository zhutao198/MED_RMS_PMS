package com.zhutao.medrms.risk.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.risk.domain.entity.RiskMatrix;
import com.zhutao.medrms.risk.service.RiskMatrixService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RiskMatrixController 单元测试（W13-D4）
 * 风险矩阵 Controller
 */
@ExtendWith(MockitoExtension.class)
class RiskMatrixControllerTest {

    @Mock private RiskMatrixService riskMatrixService;

    @InjectMocks private RiskMatrixController controller;

    private RiskMatrix newMatrix() {
        RiskMatrix m = new RiskMatrix();
        m.setId(1L);
        return m;
    }

    @Test
    @DisplayName("listByProject-返回项目矩阵列表")
    void listByProject() {
        when(riskMatrixService.listByProject(100L)).thenReturn(List.of(newMatrix()));

        Result<List<RiskMatrix>> result = controller.listByProject(100L);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    @DisplayName("listByType-按 matrixType 过滤")
    void listByType() {
        when(riskMatrixService.listByType("FMEA")).thenReturn(List.of(newMatrix()));

        Result<List<RiskMatrix>> result = controller.listByType("FMEA");

        assertEquals(1, result.getData().size());
        verify(riskMatrixService).listByType("FMEA");
    }

    @Test
    @DisplayName("getById-返回矩阵详情")
    void getById() {
        when(riskMatrixService.getById(1L)).thenReturn(newMatrix());

        Result<RiskMatrix> result = controller.getById(1L);

        assertEquals(1L, result.getData().getId());
    }

    @Test
    @DisplayName("create-创建矩阵")
    void create() {
        RiskMatrix input = new RiskMatrix();
        when(riskMatrixService.create(input)).thenReturn(newMatrix());

        Result<RiskMatrix> result = controller.create(input);

        assertNotNull(result.getData());
        verify(riskMatrixService).create(input);
    }

    @Test
    @DisplayName("update-更新矩阵")
    void update() {
        when(riskMatrixService.update(eq(1L), any(RiskMatrix.class))).thenReturn(newMatrix());

        Result<RiskMatrix> result = controller.update(1L, new RiskMatrix());

        assertNotNull(result.getData());
        verify(riskMatrixService).update(eq(1L), any(RiskMatrix.class));
    }

    @Test
    @DisplayName("calculateResidual-重算剩余风险")
    void calculateResidual() {
        when(riskMatrixService.calculateResidual(1L, "防护罩")).thenReturn(newMatrix());

        Result<RiskMatrix> result = controller.calculateResidual(1L, "防护罩");

        assertNotNull(result.getData());
        verify(riskMatrixService).calculateResidual(1L, "防护罩");
    }
}
