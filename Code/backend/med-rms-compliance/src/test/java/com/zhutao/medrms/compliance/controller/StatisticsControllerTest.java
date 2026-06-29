package com.zhutao.medrms.compliance.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.compliance.domain.entity.StatisticsSnapshot;
import com.zhutao.medrms.compliance.service.StatisticsService;
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
 * StatisticsController 单元测试（W15-D2）
 * CQRS Lite 统计查询 Controller
 */
@ExtendWith(MockitoExtension.class)
class StatisticsControllerTest {

    @Mock private StatisticsService statisticsService;

    @InjectMocks private StatisticsController controller;

    @Test
    @DisplayName("requirementStats-按 projectId 过滤")
    void requirementStats() {
        when(statisticsService.getRequirementStats(1L)).thenReturn(Map.of("total", 50, "draft", 10));

        Result<Map<String, Object>> result = controller.requirementStats(1L);

        assertEquals(50, result.getData().get("total"));
        assertEquals(10, result.getData().get("draft"));
    }

    @Test
    @DisplayName("requirementStats-无 projectId 时全局统计")
    void requirementStats_global() {
        when(statisticsService.getRequirementStats(null)).thenReturn(Map.of("total", 1000));

        Result<Map<String, Object>> result = controller.requirementStats(null);

        assertEquals(1000, result.getData().get("total"));
    }

    @Test
    @DisplayName("changeStats-返回变更聚合")
    void changeStats() {
        when(statisticsService.getChangeStats(1L)).thenReturn(Map.of("open", 5, "closed", 20));

        Result<Map<String, Object>> result = controller.changeStats(1L);

        assertEquals(5, result.getData().get("open"));
        assertEquals(20, result.getData().get("closed"));
    }

    @Test
    @DisplayName("riskStats-返回风险等级分布")
    void riskStats() {
        when(statisticsService.getRiskStats(1L)).thenReturn(Map.of("high", 3, "medium", 7, "low", 12));

        Result<Map<String, Object>> result = controller.riskStats(1L);

        assertEquals(3, result.getData().get("high"));
        assertEquals(7, result.getData().get("medium"));
        assertEquals(12, result.getData().get("low"));
    }

    @Test
    @DisplayName("complianceStats-返回合规审计统计")
    void complianceStats() {
        when(statisticsService.getComplianceStats(1L))
                .thenReturn(Map.of("auditLogs", 100, "issues", 2));

        Result<Map<String, Object>> result = controller.complianceStats(1L);

        assertEquals(100, result.getData().get("auditLogs"));
    }

    @Test
    @DisplayName("trends-返回趋势数据")
    void trends() {
        when(statisticsService.getTrends(1L)).thenReturn(Map.of("7d", List.of(1, 2, 3)));

        Result<Map<String, Object>> result = controller.trends(1L);

        assertNotNull(result.getData().get("7d"));
    }

    @Test
    @DisplayName("snapshots-查询已缓存统计快照")
    void snapshots() {
        StatisticsSnapshot s = new StatisticsSnapshot();
        s.setId(1L);
        s.setProjectId(1L);
        s.setMetricType("REQUIREMENT");
        when(statisticsService.getSnapshot(1L, "REQUIREMENT")).thenReturn(List.of(s));

        Result<List<StatisticsSnapshot>> result = controller.snapshots(1L, "REQUIREMENT");

        assertEquals(1, result.getData().size());
        assertEquals(1L, result.getData().get(0).getId());
    }
}
