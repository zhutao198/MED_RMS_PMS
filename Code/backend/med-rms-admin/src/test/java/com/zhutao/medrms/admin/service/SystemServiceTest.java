package com.zhutao.medrms.admin.service;

import com.zhutao.medrms.admin.domain.entity.DictItem;
import com.zhutao.medrms.admin.domain.entity.Role;
import com.zhutao.medrms.admin.mapper.DictItemMapper;
import com.zhutao.medrms.admin.mapper.RoleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SystemService 单元测试（W11-D2）
 * 字典 + 角色管理
 */
@ExtendWith(MockitoExtension.class)
class SystemServiceTest {

    @Mock private DictItemMapper dictItemMapper;
    @Mock private RoleMapper roleMapper;

    @InjectMocks private SystemService service;

    // ============================================================
    // 1. 字典管理
    // ============================================================

    @Test
    @DisplayName("getDictsByType-按类型过滤")
    void getDictsByType() {
        when(dictItemMapper.selectByType("PRIORITY")).thenReturn(List.of(new DictItem()));
        assertEquals(1, service.getDictsByType("PRIORITY").size());
    }

    @Test
    @DisplayName("getAllDicts-透传")
    void getAllDicts() {
        when(dictItemMapper.selectAll()).thenReturn(List.of(new DictItem(), new DictItem()));
        assertEquals(2, service.getAllDicts().size());
    }

    @Test
    @DisplayName("createDict-插入")
    void createDict() {
        DictItem item = new DictItem();
        item.setDictType("PRIORITY");
        item.setDictCode("P0");

        DictItem result = service.createDict(item);

        verify(dictItemMapper).insert(item);
        assertNotNull(result);
    }

    @Test
    @DisplayName("updateDict-部分字段更新")
    void updateDict() {
        DictItem existing = new DictItem();
        existing.setId(1L);
        existing.setDictName("OLD");
        when(dictItemMapper.selectById(1L)).thenReturn(existing);

        DictItem patch = new DictItem();
        patch.setDictName("NEW");
        patch.setStatus("ACTIVE");

        DictItem result = service.updateDict(1L, patch);

        assertEquals("NEW", result.getDictName());
        assertEquals("ACTIVE", result.getStatus());
        verify(dictItemMapper).updateById(existing);
    }

    @Test
    @DisplayName("deleteDict-存在则软删除")
    void deleteDict() {
        DictItem item = new DictItem();
        item.setId(1L);
        item.setIsDeleted(false);
        when(dictItemMapper.selectById(1L)).thenReturn(item);

        service.deleteDict(1L);

        assertTrue(item.getIsDeleted());
        verify(dictItemMapper).updateById(item);
    }

    // ============================================================
    // 2. 角色管理
    // ============================================================

    @Test
    @DisplayName("getRoles-透传")
    void getRoles() {
        when(roleMapper.selectList(any())).thenReturn(List.of(new Role()));
        assertEquals(1, service.getRoles().size());
    }

    @Test
    @DisplayName("createRole-插入")
    void createRole() {
        Role r = new Role();
        r.setRoleCode("PM");
        r.setRoleName("Project Manager");

        Role result = service.createRole(r);

        verify(roleMapper).insert(r);
        assertNotNull(result);
    }

    @Test
    @DisplayName("updateRole-部分字段更新（描述）")
    void updateRole() {
        Role existing = new Role();
        existing.setId(1L);
        existing.setRoleName("OLD");
        existing.setDescription("old desc");
        when(roleMapper.selectById(1L)).thenReturn(existing);

        Role patch = new Role();
        patch.setDescription("new desc");

        Role result = service.updateRole(1L, patch);

        assertEquals("new desc", result.getDescription());
        verify(roleMapper).updateById(existing);
    }

    @Test
    @DisplayName("deleteRole-存在则软删除")
    void deleteRole() {
        Role role = new Role();
        role.setId(1L);
        role.setIsDeleted(false);
        when(roleMapper.selectById(1L)).thenReturn(role);

        service.deleteRole(1L);

        assertTrue(role.getIsDeleted());
        verify(roleMapper).updateById(role);
    }
}
