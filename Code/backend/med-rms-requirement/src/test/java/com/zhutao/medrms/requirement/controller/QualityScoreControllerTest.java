package com.zhutao.medrms.requirement.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.requirement.service.QualityScoreService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QualityScoreController 单元测试（W13-D4）
 * FR-2.4 需求质量评分 Controller
 */
@ExtendWith(MockitoExtension.class)
class QualityScoreControllerTest {

    @Mock private QualityScoreService qualityScoreService;

    @InjectMocks private QualityScoreController controller;

    @Test
    @DisplayName("score-单需求评分返回 200 + data")
    void score() {
        Map<String, Object> mockResult = Map.of("score", 85, "level", "GOOD");
        when(qualityScoreService.score(1L)).thenReturn(mockResult);

        Result<Map<String, Object>> result = controller.score(1L);

        assertEquals(200, result.getCode());
        assertEquals(85, result.getData().get("score"));
    }

    @Test
    @DisplayName("scoreAll-无 projectId 时按全局批量评分")
    void scoreAll_global() {
        when(qualityScoreService.scoreAll(null)).thenReturn(List.of(Map.of("score", 90)));

        Result<List<Map<String, Object>>> result = controller.scoreAll(null);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        verify(qualityScoreService).scoreAll(null);
    }

    @Test
    @DisplayName("scoreAll-有 projectId 时按项目批量评分")
    void scoreAll_byProject() {
        when(qualityScoreService.scoreAll(100L))
                .thenReturn(List.of(Map.of("score", 75), Map.of("score", 80)));

        Result<List<Map<String, Object>>> result = controller.scoreAll(100L);

        assertEquals(2, result.getData().size());
        verify(qualityScoreService).scoreAll(100L);
    }
}
