package com.zhutao.medrms.risk.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.risk.domain.entity.RiskRegister;
import com.zhutao.medrms.risk.service.RiskRegisterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RiskRegisterController 单元测试（v1.27 R28）
 * 覆盖风险登记列表/详情/创建/更新/关闭/接受
 */
@ExtendWith(MockitoExtension.class)
class RiskRegisterControllerTest {

    @Mock
    private RiskRegisterService riskRegisterService;

    @InjectMocks
    private RiskRegisterController controller;

    @Test
    void list_returnsRisks() {
        RiskRegister r = new RiskRegister(); r.setId(1L); r.setRiskTitle("R1");
        when(riskRegisterService.list(null, null, null)).thenReturn(Arrays.asList(r));

        Result<List<RiskRegister>> result = controller.list(null, null, null);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    void list_filtered() {
        when(riskRegisterService.list("OPEN", "SOFTWARE", null)).thenReturn(List.of(new RiskRegister()));

        Result<List<RiskRegister>> result = controller.list("OPEN", "SOFTWARE", null);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    // R109 G3 新增：按项目过滤
    @Test
    void list_filteredByProject() {
        when(riskRegisterService.list(null, null, 1L)).thenReturn(List.of(new RiskRegister()));

        Result<List<RiskRegister>> result = controller.list(null, null, 1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    void getById_returnsRisk() {
        RiskRegister r = new RiskRegister(); r.setId(1L); r.setRiskTitle("R1");
        when(riskRegisterService.getById(1L)).thenReturn(r);

        Result<RiskRegister> result = controller.getById(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("R1", result.getData().getRiskTitle());
    }

    @Test
    void create_returnsNewRisk() {
        RiskRegister input = new RiskRegister(); input.setRiskTitle("R-NEW");
        RiskRegister saved = new RiskRegister(); saved.setId(50L); saved.setRiskTitle("R-NEW");
        when(riskRegisterService.create(any())).thenReturn(saved);

        Result<RiskRegister> result = controller.create(input);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(50L, result.getData().getId());
    }

    @Test
    void update_returnsUpdated() {
        RiskRegister updates = new RiskRegister(); updates.setRiskTitle("R-UPD");
        when(riskRegisterService.update(eq(1L), any())).thenReturn(updates);

        Result<RiskRegister> result = controller.update(1L, updates);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("R-UPD", result.getData().getRiskTitle());
    }

    @Test
    void close_returnsClosed() {
        RiskRegister closed = new RiskRegister(); closed.setId(1L); closed.setRiskLevel("CLOSED");
        when(riskRegisterService.close(eq(1L), eq("已缓解"))).thenReturn(closed);

        Result<RiskRegister> result = controller.close(1L, "已缓解");

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void acceptRisk_returnsAccepted() {
        RiskRegister accepted = new RiskRegister(); accepted.setId(1L);
        accepted.setResponseStrategy("ACCEPT");
        when(riskRegisterService.acceptRisk(1L)).thenReturn(accepted);

        Result<RiskRegister> result = controller.acceptRisk(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("ACCEPT", result.getData().getResponseStrategy());
    }
}
