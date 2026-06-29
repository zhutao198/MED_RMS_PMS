package com.zhutao.medrms.project.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.project.domain.entity.IpdGate;
import com.zhutao.medrms.project.service.IpdGateService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * IpdGateController 单元测试（v1.27 R28）
 * 覆盖 IPD 阶段门列表/创建/通过/失败/自动检查
 */
@ExtendWith(MockitoExtension.class)
class IpdGateControllerTest {

    @Mock
    private IpdGateService ipdGateService;

    @InjectMocks
    private IpdGateController controller;

    @Test
    void listByProject_returnsGates() {
        IpdGate g = new IpdGate(); g.setId(1L); g.setGateName("DCP1");
        when(ipdGateService.listByProject(1L)).thenReturn(List.of(g));

        Result<List<IpdGate>> result = controller.listByProject(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    void getById_returnsGate() {
        IpdGate g = new IpdGate(); g.setId(1L); g.setGateName("DCP2");
        when(ipdGateService.getById(1L)).thenReturn(g);

        Result<IpdGate> result = controller.getById(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void create_returnsNewGate() {
        IpdGate input = new IpdGate(); input.setGateName("DCP1");
        IpdGate saved = new IpdGate(); saved.setId(99L); saved.setGateName("DCP1");
        when(ipdGateService.create(any())).thenReturn(saved);

        Result<IpdGate> result = controller.create(input);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(99L, result.getData().getId());
    }

    @Test
    void passGate_returnsGate() {
        IpdGate g = new IpdGate(); g.setId(1L); g.setStatus("PASSED");
        when(ipdGateService.passGate(eq(1L), eq("APPROVED"), any())).thenReturn(g);

        Result<IpdGate> result = controller.passGate(1L, "APPROVED", "all good");

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("PASSED", result.getData().getStatus());
    }

    @Test
    void failGate_returnsGate() {
        IpdGate g = new IpdGate(); g.setId(1L); g.setStatus("FAILED");
        when(ipdGateService.failGate(eq(1L), eq("not ready"))).thenReturn(g);

        Result<IpdGate> result = controller.failGate(1L, "not ready");

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("FAILED", result.getData().getStatus());
    }

    @Test
    void autoCheck_returnsMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("gate", "DCP1");
        result.put("allPass", true);
        when(ipdGateService.autoCheckGate(eq(1L), eq(1), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(result);

        Result<Map<String, Object>> res = controller.autoCheck(1L, 1, 10, 5, 3, 1, 8, 6, 12, 14, 20);

        assertNotNull(res);
        assertEquals(200, res.getCode());
        assertEquals(true, res.getData().get("allPass"));
    }
}
