package com.zhutao.medrms.traceability.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.traceability.service.TraceGraphService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * TraceGraphController 单元测试（W15-D3）
 * 追溯图谱与质量评分 Controller
 */
@ExtendWith(MockitoExtension.class)
class TraceGraphControllerTest {

    @Mock private TraceGraphService traceGraphService;

    @InjectMocks private TraceGraphController controller;

    @Test
    @DisplayName("getTraceGraph-返回项目追溯图谱")
    @SuppressWarnings("unchecked")
    void getTraceGraph() {
        Map<String, Object> graph = Map.of(
                "nodes", List.of(Map.of("id", 1)),
                "edges", List.of(),
                "stats", Map.of("total", 1));
        when(traceGraphService.getTraceGraph(1L)).thenReturn(graph);

        Result<Map<String, Object>> result = controller.getTraceGraph(1L);

        assertEquals(200, result.getCode());
        assertNotNull(result.getData().get("nodes"));
        assertNotNull(result.getData().get("edges"));
        assertNotNull(result.getData().get("stats"));
    }

    @Test
    @DisplayName("getTraceGraph-无数据：返回空图")
    @SuppressWarnings("unchecked")
    void getTraceGraph_empty() {
        when(traceGraphService.getTraceGraph(99L)).thenReturn(Map.of());

        Result<Map<String, Object>> result = controller.getTraceGraph(99L);

        assertTrue(result.getData().isEmpty());
    }

    @Test
    @DisplayName("getQualityScore-单需求评分")
    void getQualityScore() {
        when(traceGraphService.getQualityScore(1L))
                .thenReturn(Map.of("score", 85, "level", "GOOD"));

        Result<Map<String, Object>> result = controller.getQualityScore(1L);

        assertEquals(85, result.getData().get("score"));
        assertEquals("GOOD", result.getData().get("level"));
    }

    @Test
    @DisplayName("getQualityScore-需求不存在：score=0 + level=UNKNOWN")
    void getQualityScore_notFound() {
        when(traceGraphService.getQualityScore(99L))
                .thenReturn(Map.of("score", 0, "level", "UNKNOWN"));

        Result<Map<String, Object>> result = controller.getQualityScore(99L);

        assertEquals(0, result.getData().get("score"));
    }

    @Test
    @DisplayName("batchQualityScore-项目下所有需求批量评分")
    @SuppressWarnings("unchecked")
    void batchQualityScore() {
        List<Map<String, Object>> scores = List.of(
                Map.of("requirementId", 1, "score", 80),
                Map.of("requirementId", 2, "score", 90));
        when(traceGraphService.getBatchQualityScore(1L)).thenReturn(scores);

        Result<List<Map<String, Object>>> result = controller.batchQualityScore(1L);

        assertEquals(2, result.getData().size());
    }

    @Test
    @DisplayName("batchQualityScore-无需求：返回空列表")
    @SuppressWarnings("unchecked")
    void batchQualityScore_empty() {
        when(traceGraphService.getBatchQualityScore(99L)).thenReturn(List.of());

        Result<List<Map<String, Object>>> result = controller.batchQualityScore(99L);

        assertTrue(result.getData().isEmpty());
    }
}
