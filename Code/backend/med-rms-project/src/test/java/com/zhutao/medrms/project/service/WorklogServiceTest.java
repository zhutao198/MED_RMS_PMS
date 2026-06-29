package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.project.domain.entity.Worklog;
import com.zhutao.medrms.project.mapper.WorklogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorklogService 单元测试（W12-D2）
 * FR-2.9 工时统计
 */
@ExtendWith(MockitoExtension.class)
class WorklogServiceTest {

    @Mock private WorklogMapper worklogMapper;

    @InjectMocks private WorklogService service;

    private Worklog newLog(Long taskId, Long workerId, String workerName, BigDecimal hours) {
        Worklog l = new Worklog();
        l.setTaskId(taskId);
        l.setWorkerId(workerId);
        l.setWorkerName(workerName);
        l.setHours(hours);
        return l;
    }

    // ============================================================
    // 1. 创建（参数校验）
    // ============================================================

    @Test
    @DisplayName("create-工时>0 成功")
    void create_ok() {
        Worklog l = newLog(1L, 1L, "张三", new BigDecimal("2.5"));

        Worklog result = service.create(l);

        verify(worklogMapper).insert(l);
        assertEquals(new BigDecimal("2.5"), result.getHours());
    }

    @Test
    @DisplayName("create-工时为 0 抛 IllegalArgumentException")
    void create_zeroHours() {
        Worklog l = newLog(1L, 1L, "张三", BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> service.create(l));
    }

    @Test
    @DisplayName("create-工时为负抛 IllegalArgumentException")
    void create_negativeHours() {
        Worklog l = newLog(1L, 1L, "张三", new BigDecimal("-1"));
        assertThrows(IllegalArgumentException.class, () -> service.create(l));
    }

    // ============================================================
    // 2. 汇总（按维度聚合）
    // ============================================================

    @Test
    @DisplayName("summary-按 worker+task 维度聚合")
    void summary_groupedByWorkerAndTask() {
        Worklog a = newLog(10L, 1L, "张三", new BigDecimal("2.0"));
        Worklog b = newLog(10L, 1L, "张三", new BigDecimal("1.5"));
        Worklog c = newLog(20L, 2L, "李四", new BigDecimal("3.0"));
        when(worklogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(a, b, c));

        Map<String, Object> result = service.summary(1L, null, null);

        assertEquals(3, result.get("count"));
        assertEquals(new BigDecimal("6.5"), result.get("totalHours"));
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> byWorker = (Map<String, BigDecimal>) result.get("byWorker");
        assertEquals(new BigDecimal("3.5"), byWorker.get("张三"));
        assertEquals(new BigDecimal("3.0"), byWorker.get("李四"));
        @SuppressWarnings("unchecked")
        Map<Long, BigDecimal> byTask = (Map<Long, BigDecimal>) result.get("byTask");
        assertEquals(new BigDecimal("3.5"), byTask.get(10L));
        assertEquals(new BigDecimal("3.0"), byTask.get(20L));
    }

    @Test
    @DisplayName("summary-无数据返回 0/0/空")
    void summary_empty() {
        when(worklogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        Map<String, Object> result = service.summary(null, null, null);

        assertEquals(0, result.get("count"));
        assertEquals(BigDecimal.ZERO, result.get("totalHours"));
    }
}
