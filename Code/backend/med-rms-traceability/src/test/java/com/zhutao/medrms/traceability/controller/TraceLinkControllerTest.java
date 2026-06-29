package com.zhutao.medrms.traceability.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.traceability.domain.entity.TraceLink;
import com.zhutao.medrms.traceability.service.TraceabilityService;
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
 * TraceLinkController 单元测试（W13-D4）
 * v1.47 BUG #134 P0 修复：TraceLink 9 个 CRUD 端点
 */
@ExtendWith(MockitoExtension.class)
class TraceLinkControllerTest {

    @Mock private TraceabilityService traceabilityService;

    @InjectMocks private TraceLinkController controller;

    private TraceLink newLink() {
        TraceLink l = new TraceLink();
        l.setId(1L);
        return l;
    }

    @Test
    @DisplayName("createTraceLink-创建追溯链接")
    void create() {
        when(traceabilityService.createTraceLink(any(TraceLink.class))).thenReturn(newLink());

        Result<TraceLink> result = controller.createTraceLink(new TraceLink());

        assertEquals(200, result.getCode());
        assertEquals(1L, result.getData().getId());
    }

    @Test
    @DisplayName("updateTraceLink-更新返回 boolean")
    void update() {
        when(traceabilityService.updateTraceLink(eq(1L), any(TraceLink.class))).thenReturn(true);

        Result<Boolean> result = controller.updateTraceLink(1L, new TraceLink());

        assertTrue(result.getData());
    }

    @Test
    @DisplayName("deleteTraceLink-软删除返回 boolean")
    void delete() {
        when(traceabilityService.deleteTraceLink(1L)).thenReturn(true);

        Result<Boolean> result = controller.deleteTraceLink(1L);

        assertTrue(result.getData());
    }

    @Test
    @DisplayName("getTraceLinkById-查询详情")
    void getById() {
        when(traceabilityService.getTraceLinkById(1L)).thenReturn(newLink());

        Result<TraceLink> result = controller.getTraceLinkById(1L);

        assertEquals(1L, result.getData().getId());
    }

    @Test
    @DisplayName("listTraceLinks-按 projectId + 可选 linkType 过滤")
    void list() {
        when(traceabilityService.listTraceLinks(100L, "DEPENDS"))
                .thenReturn(List.of(newLink()));

        Result<List<TraceLink>> result = controller.listTraceLinks(100L, "DEPENDS");

        assertEquals(1, result.getData().size());
    }

    @Test
    @DisplayName("listBySource-按源端 ID 查询")
    void listBySource() {
        when(traceabilityService.listBySource(10L)).thenReturn(List.of(newLink()));

        Result<List<TraceLink>> result = controller.listBySource(10L);

        assertEquals(1, result.getData().size());
    }

    @Test
    @DisplayName("listByTarget-按目标 ID 查询")
    void listByTarget() {
        when(traceabilityService.listByTarget(20L)).thenReturn(List.of(newLink()));

        Result<List<TraceLink>> result = controller.listByTarget(20L);

        assertEquals(1, result.getData().size());
    }

    @Test
    @DisplayName("checkCycle-无环校验")
    void checkCycle() {
        when(traceabilityService.wouldCreateCycle(10L, 20L)).thenReturn(false);

        Result<Boolean> result = controller.checkCycle(10L, 20L);

        assertFalse(result.getData());
    }

    @Test
    @DisplayName("listByPair-按 (source, target) 对查询")
    void listByPair() {
        when(traceabilityService.listByPair(10L, 20L)).thenReturn(List.of(newLink()));

        Result<List<TraceLink>> result = controller.listByPair(10L, 20L);

        assertEquals(1, result.getData().size());
        verify(traceabilityService).listByPair(10L, 20L);
    }
}
