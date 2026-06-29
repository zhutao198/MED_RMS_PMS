package com.zhutao.medrms.project.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.project.domain.entity.Project;
import com.zhutao.medrms.project.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ProjectController 单元测试（v1.27 R28）
 * 覆盖项目列表/详情/创建/更新 4 个核心端点
 */
@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

    @Mock
    private ProjectService projectService;

    @InjectMocks
    private ProjectController controller;

    @Test
    void list_returnsProjects() {
        Project p1 = new Project(); p1.setId(1L); p1.setProjectName("P1"); p1.setStatus("PLANNING");
        Project p2 = new Project(); p2.setId(2L); p2.setProjectName("P2"); p2.setStatus("ACTIVE");
        when(projectService.list(null)).thenReturn(Arrays.asList(p1, p2));

        Result<?> result = controller.list(null);

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void list_filteredByStatus() {
        when(projectService.list("ACTIVE")).thenReturn(Arrays.asList(new Project()));

        Result<?> result = controller.list("ACTIVE");

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(projectService, times(1)).list("ACTIVE");
    }

    @Test
    void getById_returnsProject() {
        Project p = new Project(); p.setId(5L); p.setProjectName("P5");
        when(projectService.getById(5L)).thenReturn(p);

        Result<Project> result = controller.getById(5L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(5L, result.getData().getId());
    }

    @Test
    void create_returnsNewProject() {
        Project input = new Project();
        input.setProjectName("New");
        Project saved = new Project();
        saved.setId(10L);
        saved.setProjectName("New");
        saved.setCreatedAt(LocalDateTime.now());
        when(projectService.create(any())).thenReturn(saved);

        Result<Project> result = controller.create(input);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(10L, result.getData().getId());
        verify(projectService, times(1)).create(input);
    }

    @Test
    void update_returnsUpdatedProject() {
        Project updates = new Project();
        updates.setProjectName("Renamed");
        when(projectService.update(eq(1L), any())).thenReturn(updates);

        Result<Project> result = controller.update(1L, updates);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("Renamed", result.getData().getProjectName());
    }
}
