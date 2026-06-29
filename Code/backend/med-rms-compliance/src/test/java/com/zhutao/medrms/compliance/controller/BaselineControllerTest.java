package com.zhutao.medrms.compliance.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.compliance.domain.entity.Baseline;
import com.zhutao.medrms.compliance.service.BaselineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * BaselineController 单元测试（v1.27 R28，v1.48 P0 #2 修复：迁移到 compliance 模块）
 * 覆盖基线创建/列表/对比 3 个核心端点
 */
@ExtendWith(MockitoExtension.class)
class BaselineControllerTest {

    @Mock
    private BaselineService baselineService;

    @InjectMocks
    private BaselineController controller;

    @Test
    void create_returnsSuccess() {
        BaselineController.CreateBaselineRequest req = new BaselineController.CreateBaselineRequest();
        req.setProjectId(1L);
        req.setName("BL-1.0");
        req.setRequirementIds(Arrays.asList(10L, 20L));

        Baseline saved = new Baseline();
        saved.setId(100L);
        saved.setBaselineName("BL-1.0");
        when(baselineService.createBaseline(anyLong(), any(), any())).thenReturn(saved);

        Result<Baseline> result = controller.create(req);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals("BL-1.0", result.getData().getBaselineName());
        verify(baselineService, times(1)).createBaseline(eq(1L), eq("BL-1.0"), any());
    }

    @Test
    void getByProject_returnsAllBaselines() {
        Baseline b1 = new Baseline(); b1.setId(1L); b1.setBaselineName("BL-1");
        Baseline b2 = new Baseline(); b2.setId(2L); b2.setBaselineName("BL-2");
        when(baselineService.getByProject(1L)).thenReturn(Arrays.asList(b1, b2));

        Result<List<Baseline>> result = controller.getByProject(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().size());
    }

    @Test
    void compare_returnsDiff() {
        Map<String, Object> diff = new HashMap<>();
        diff.put("addedCount", 3);
        diff.put("removedCount", 1);
        diff.put("modifiedCount", 0);
        when(baselineService.compare(1L, 2L)).thenReturn(diff);

        Result<Map<String, Object>> result = controller.compare(1L, 2L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(3, result.getData().get("addedCount"));
    }
}
