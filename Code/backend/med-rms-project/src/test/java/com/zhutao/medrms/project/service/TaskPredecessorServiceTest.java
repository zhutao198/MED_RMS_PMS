package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.project.domain.entity.Task;
import com.zhutao.medrms.project.domain.entity.TaskPredecessor;
import com.zhutao.medrms.project.mapper.TaskMapper;
import com.zhutao.medrms.project.mapper.TaskPredecessorMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TaskPredecessorService 单元测试（W12-D2）
 * v1.43 P1-3 任务前置依赖持久化 + 环检测
 */
@ExtendWith(MockitoExtension.class)
class TaskPredecessorServiceTest {

    @Mock private TaskPredecessorMapper predecessorMapper;
    @Mock private TaskMapper taskMapper;

    @InjectMocks private TaskPredecessorService service;

    private Task newTask(Long id) {
        Task t = new Task();
        t.setId(id);
        t.setProjectId(1L);
        return t;
    }

    private TaskPredecessor newPred(Long taskId, Long predId) {
        TaskPredecessor tp = new TaskPredecessor();
        tp.setTaskId(taskId);
        tp.setPredecessorId(predId);
        return tp;
    }

    // ============================================================
    // 1. 查询
    // ============================================================

    @Test
    @DisplayName("listPredecessorIds-透传 mapper")
    void listPredecessorIds() {
        when(predecessorMapper.selectByTaskId(10L))
                .thenReturn(List.of(newPred(10L, 1L), newPred(10L, 2L)));
        assertEquals(List.of(1L, 2L), service.listPredecessorIds(10L));
    }

    // ============================================================
    // 2. 更新（PUT 语义 + 自依赖移除 + 存在性 + 环检测）
    // ============================================================

    @Test
    @DisplayName("updatePredecessors-成功：先删后写")
    void updatePredecessors_ok() {
        when(taskMapper.selectById(10L)).thenReturn(newTask(10L));
        when(taskMapper.selectById(1L)).thenReturn(newTask(1L));
        when(taskMapper.selectById(2L)).thenReturn(newTask(2L));

        List<Long> result = service.updatePredecessors(10L, List.of(1L, 2L));

        verify(predecessorMapper).deleteByTaskId(10L);
        ArgumentCaptor<TaskPredecessor> captor = ArgumentCaptor.forClass(TaskPredecessor.class);
        verify(predecessorMapper, times(2)).insert(captor.capture());
        assertEquals(2, result.size());
        assertTrue(result.contains(1L) && result.contains(2L));
    }

    @Test
    @DisplayName("updatePredecessors-自依赖自动移除")
    void updatePredecessors_removeSelfLoop() {
        when(taskMapper.selectById(10L)).thenReturn(newTask(10L));
        when(taskMapper.selectById(20L)).thenReturn(newTask(20L));

        // 10L 是 taskId，前置包含 10L → 应被自动移除 → 仅 20 保留
        List<Long> result = service.updatePredecessors(10L, List.of(10L, 20L));

        verify(taskMapper).selectById(20L);
        verify(predecessorMapper).deleteByTaskId(10L);
        assertEquals(List.of(20L), result);
    }

    @Test
    @DisplayName("updatePredecessors-空列表等价于清空")
    void updatePredecessors_emptyClears() {
        when(taskMapper.selectById(10L)).thenReturn(newTask(10L));

        List<Long> result = service.updatePredecessors(10L, List.of());

        verify(predecessorMapper).deleteByTaskId(10L);
        verify(predecessorMapper, never()).insert(any(TaskPredecessor.class));
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("updatePredecessors-前置任务不存在抛 SY0101")
    void updatePredecessors_predNotExist() {
        when(taskMapper.selectById(10L)).thenReturn(newTask(10L));
        when(taskMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updatePredecessors(10L, List.of(99L)));
        assertEquals("SY0101", ex.getCode());
        verify(predecessorMapper, never()).insert(any(TaskPredecessor.class));
    }

    @Test
    @DisplayName("updatePredecessors-形成环抛 SY0101")
    void updatePredecessors_cycle() {
        // task=10 想加 predecessor=20
        // 但 20 已经把 10 当前置 → DFS 从 20 能到达 10 → 环
        when(taskMapper.selectById(10L)).thenReturn(newTask(10L));
        when(taskMapper.selectById(20L)).thenReturn(newTask(20L));
        when(predecessorMapper.selectByTaskId(20L))
                .thenReturn(List.of(newPred(20L, 10L)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updatePredecessors(10L, List.of(20L)));
        assertEquals("SY0101", ex.getCode());
        verify(predecessorMapper, never()).insert(any(TaskPredecessor.class));
    }

    @Test
    @DisplayName("updatePredecessors-taskId 为 null 抛 SY0101")
    void updatePredecessors_nullTaskId() {
        assertThrows(BusinessException.class,
                () -> service.updatePredecessors(null, List.of(1L)));
    }

    @Test
    @DisplayName("updatePredecessors-任务本身不存在抛 SY0301")
    void updatePredecessors_taskNotFound() {
        when(taskMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updatePredecessors(99L, List.of(1L)));
        assertEquals("SY0301", ex.getCode());
    }

    // ============================================================
    // 3. 加载项目依赖图
    // ============================================================

    @Test
    @DisplayName("loadProjectGraph-返回 taskId → predecessorIds 映射")
    void loadProjectGraph() {
        Task t1 = newTask(10L);
        Task t2 = newTask(20L);
        when(taskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(t1, t2));
        when(predecessorMapper.selectByTaskId(10L))
                .thenReturn(List.of(newPred(10L, 1L)));
        when(predecessorMapper.selectByTaskId(20L))
                .thenReturn(List.of(newPred(20L, 2L), newPred(20L, 3L)));

        Map<Long, List<Long>> graph = service.loadProjectGraph(1L);

        assertEquals(List.of(1L), graph.get(10L));
        assertEquals(List.of(2L, 3L), graph.get(20L));
    }
}
