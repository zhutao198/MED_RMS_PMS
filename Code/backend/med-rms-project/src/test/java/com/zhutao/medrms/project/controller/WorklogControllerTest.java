package com.zhutao.medrms.project.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.project.domain.entity.Worklog;
import com.zhutao.medrms.project.service.WorklogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WorklogController 单元测试（v1.27 R28）
 * 覆盖工时提交/工时汇总
 */
@ExtendWith(MockitoExtension.class)
class WorklogControllerTest {

    @Mock
    private WorklogService worklogService;

    @InjectMocks
    private WorklogController controller;

    @Test
    void create_returnsSaved() {
        Worklog input = new Worklog();
        input.setHours(new BigDecimal("2.5"));
        input.setWorkerId(1L);
        Worklog saved = new Worklog();
        saved.setId(99L);
        saved.setHours(new BigDecimal("2.5"));
        when(worklogService.create(any())).thenReturn(saved);

        Result<Worklog> result = controller.create(input);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(99L, result.getData().getId());
    }

    @Test
    void summary_returnsMap() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalHours", 100.5);
        summary.put("byProject", Map.of("P1", 60.0, "P2", 40.5));
        when(worklogService.summary(eq(1L), any(), any())).thenReturn(summary);

        Result<Map<String, Object>> result = controller.summary(1L, null, null);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(100.5, result.getData().get("totalHours"));
    }

    @Test
    void summary_allFilters() {
        when(worklogService.summary(eq(1L), eq(2L), eq(3L))).thenReturn(Map.of("count", 5));

        Result<Map<String, Object>> result = controller.summary(1L, 2L, 3L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(5, result.getData().get("count"));
    }
}
