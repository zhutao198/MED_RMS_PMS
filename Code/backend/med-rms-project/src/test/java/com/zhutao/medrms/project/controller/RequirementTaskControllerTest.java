package com.zhutao.medrms.project.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.project.domain.entity.Task;
import com.zhutao.medrms.project.service.RequirementTaskService;
import com.zhutao.medrms.project.service.RequirementTaskService.TaskDraft;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RequirementTaskController 单元测试（W14-D2）
 * FR-1.10 需求→任务转化 Controller
 */
@ExtendWith(MockitoExtension.class)
class RequirementTaskControllerTest {

    @Mock private RequirementTaskService service;

    @InjectMocks private RequirementTaskController controller;

    private Task newTask(Long id) {
        Task t = new Task();
        t.setId(id);
        t.setTitle("Task-" + id);
        return t;
    }

    @Test
    @DisplayName("generateDrafts-生成任务草稿")
    void generateDrafts() {
        when(service.generateDrafts(anyLong())).thenReturn(List.of(newTaskDraft(), newTaskDraft()));

        Result<List<TaskDraft>> result = controller.generateDrafts(1L);

        assertEquals(2, result.getData().size());
    }

    @Test
    @DisplayName("convert-需求拆解为任务")
    void convert() {
        when(service.convertRequirementToTasks(anyLong(), anyList()))
                .thenReturn(List.of(newTask(1L), newTask(2L)));

        Result<List<Task>> result = controller.convert(1L, List.of(newTaskDraft()));

        assertEquals(2, result.getData().size());
    }

    @Test
    @DisplayName("getTasks-按需求查询任务列表")
    void getTasks() {
        when(service.getTasksByRequirement(1L)).thenReturn(List.of(newTask(1L)));

        Result<List<Task>> result = controller.getTasks(1L);

        assertEquals(1, result.getData().size());
    }

    @Test
    @DisplayName("updateStatus-更新任务状态")
    void updateStatus() {
        when(service.updateTaskStatus(anyLong(), any())).thenReturn(newTask(1L));

        Result<Task> result = controller.updateStatus(1L, "IN_PROGRESS");

        assertNotNull(result.getData());
        verify(service).updateTaskStatus(1L, "IN_PROGRESS");
    }

    @Test
    @DisplayName("getProgress-查询转化进度")
    void getProgress() {
        when(service.getRequirementProgress(anyLong()))
                .thenReturn(Map.of("total", 10, "done", 5));

        Result<Map<String, Object>> result = controller.getProgress(1L);

        assertEquals(10, result.getData().get("total"));
        assertEquals(5, result.getData().get("done"));
    }

    private TaskDraft newTaskDraft() {
        return new TaskDraft("title", "desc",
                java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(7),
                8, "PM", 1L, "M", 100L, 200L);
    }
}
