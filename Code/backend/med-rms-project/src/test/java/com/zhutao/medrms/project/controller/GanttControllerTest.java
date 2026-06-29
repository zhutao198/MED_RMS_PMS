package com.zhutao.medrms.project.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.project.domain.entity.Milestone;
import com.zhutao.medrms.project.domain.entity.Task;
import com.zhutao.medrms.project.mapper.MilestoneMapper;
import com.zhutao.medrms.project.mapper.TaskMapper;
import com.zhutao.medrms.project.service.GanttService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * GanttController 单元测试（v1.27 R28）
 * 覆盖甘特图/资源负载/任务/里程碑/阶段门检查
 */
@ExtendWith(MockitoExtension.class)
class GanttControllerTest {

    @Mock
    private GanttService ganttService;

    @Mock
    private MilestoneMapper milestoneMapper;

    @Mock
    private TaskMapper taskMapper;

    @InjectMocks
    private GanttController controller;

    @Test
    void getGanttData_returnsMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("milestones", java.util.List.of(new Milestone()));
        data.put("tasks", java.util.List.of(new Task()));
        when(ganttService.getGanttData(1L)).thenReturn(data);

        Result<Map<String, Object>> result = controller.getGanttData(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().size());
        assertNotNull(result.getData().get("milestones"));
    }

    @Test
    void getResourceLoad_returnsMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("load", 0.75);
        when(ganttService.getResourceLoad(1L)).thenReturn(data);

        Result<Map<String, Object>> result = controller.getResourceLoad(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(0.75, result.getData().get("load"));
    }

    @Test
    void createTask_returnsSaved() {
        Task input = new Task();
        input.setTaskNo("T-001");
        Task saved = new Task();
        saved.setId(10L);
        saved.setTaskNo("T-001");
        when(ganttService.createTask(any())).thenReturn(saved);

        Result<Task> result = controller.createTask(input);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(10L, result.getData().getId());
    }

    @Test
    void createMilestone_returnsSaved() {
        Milestone input = new Milestone();
        input.setMilestoneNo("M-001");
        input.setPlannedDate(LocalDate.now());
        Milestone saved = new Milestone();
        saved.setId(20L);
        saved.setMilestoneNo("M-001");
        when(ganttService.createMilestone(any())).thenReturn(saved);

        Result<Milestone> result = controller.createMilestone(input);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(20L, result.getData().getId());
    }

    @Test
    void checkGate_returnsMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("pass", true);
        when(ganttService.checkGate(1L)).thenReturn(data);

        Result<Map<String, Object>> result = controller.checkGate(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(true, result.getData().get("pass"));
    }

    @Test
    void getMilestones_returnsList() {
        when(milestoneMapper.selectList(any(Wrapper.class))).thenReturn(java.util.Collections.emptyList());

        Result<?> result = controller.getMilestones(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void getTasks_returnsList() {
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(java.util.Collections.emptyList());

        Result<?> result = controller.getTasks(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }
}
