package com.zhutao.medrms.project.service;

import com.zhutao.medrms.project.domain.entity.Milestone;
import com.zhutao.medrms.project.domain.entity.Task;
import com.zhutao.medrms.project.mapper.MilestoneMapper;
import com.zhutao.medrms.project.mapper.TaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GanttService 单元测试（W10-D1）
 */
@ExtendWith(MockitoExtension.class)
class GanttServiceTest {

    @Mock private TaskMapper taskMapper;
    @Mock private MilestoneMapper milestoneMapper;

    @InjectMocks private GanttService service;

    @Test
    @DisplayName("getGanttData-返回 tasks + milestones")
    void getGanttData() {
        when(taskMapper.selectList(any())).thenReturn(List.of(new Task()));
        when(milestoneMapper.selectList(any())).thenReturn(List.of(new Milestone()));

        Map<String, Object> data = service.getGanttData(1L);

        assertNotNull(data);
        assertTrue(data.containsKey("tasks"));
        assertTrue(data.containsKey("milestones"));
    }

    @Test
    @DisplayName("getResourceLoad-空数据返回 0 负载")
    void getResourceLoad_empty() {
        when(taskMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> data = service.getResourceLoad(1L);

        assertNotNull(data);
    }

    @Test
    @DisplayName("createTask-插入")
    void createTask() {
        Task t = new Task();
        t.setProjectId(1L);
        t.setTitle("Test Task");

        Task result = service.createTask(t);

        verify(taskMapper).insert(t);
        assertSame(t, result);
    }

    @Test
    @DisplayName("createMilestone-插入")
    void createMilestone() {
        Milestone m = new Milestone();
        m.setProjectId(1L);
        m.setName("M1");

        Milestone result = service.createMilestone(m);

        verify(milestoneMapper).insert(m);
        assertSame(m, result);
    }
}
