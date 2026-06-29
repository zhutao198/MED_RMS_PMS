package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.project.domain.entity.Project;
import com.zhutao.medrms.project.mapper.ProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ProjectService 单元测试（W10-D1）
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock private ProjectMapper projectMapper;

    @InjectMocks private ProjectService service;

    @Test
    @DisplayName("list-透传 mapper")
    void list() {
        when(projectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(new Project()));
        assertEquals(1, service.list(null).size());
    }

    @Test
    @DisplayName("list-按状态过滤")
    void list_byStatus() {
        when(projectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        assertEquals(0, service.list("InProgress").size());
    }

    @Test
    @DisplayName("getById-不存在抛 BusinessException")
    void getById_notFound() {
        when(projectMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.getById(99L));
    }

    @Test
    @DisplayName("getById-存在返回")
    void getById_exists() {
        Project p = new Project();
        p.setId(1L);
        when(projectMapper.selectById(1L)).thenReturn(p);

        assertSame(p, service.getById(1L));
    }

    @Test
    @DisplayName("create-插入并返回")
    void create() {
        Project p = new Project();
        p.setProjectName("Test");
        p.setProjectNo("P-001");

        Project result = service.create(p);

        verify(projectMapper).insert(p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("update-部分字段更新")
    void update() {
        Project existing = new Project();
        existing.setId(1L);
        existing.setProjectName("OLD");
        when(projectMapper.selectById(1L)).thenReturn(existing);

        Project patch = new Project();
        patch.setProjectName("NEW");
        patch.setDescription("desc");

        Project result = service.update(1L, patch);

        assertEquals("NEW", result.getProjectName());
        assertEquals("desc", result.getDescription());
        verify(projectMapper).updateById(existing);
    }

    @Test
    @DisplayName("update-不存在抛 BusinessException")
    void update_notFound() {
        when(projectMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class,
            () -> service.update(99L, new Project()));
    }
}
