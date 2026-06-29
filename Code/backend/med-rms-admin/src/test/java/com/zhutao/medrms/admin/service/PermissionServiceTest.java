package com.zhutao.medrms.admin.service;

import com.zhutao.medrms.admin.mapper.PermissionMapper;
import com.zhutao.medrms.admin.mapper.RoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * PermissionService 单元测试
 * 验证 8 角色权限矩阵核心场景
 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private PermissionMapper permissionMapper;

    @InjectMocks
    private PermissionService permissionService;

    @Test
    void adminShouldHaveAllPermissions() {
        when(roleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));
        Set<String> perms = permissionService.getUserPermCodes(1L);
        assertTrue(perms.contains("*"));
    }

    @Test
    void viewerShouldOnlyHaveReadPermissions() {
        when(roleMapper.selectRoleCodesByUserId(8L)).thenReturn(List.of("VIEWER"));
        when(permissionMapper.selectPermCodesByRoleCodes(anySet()))
            .thenReturn(List.of("req:list", "trace:matrix", "report:dashboard", "esign:read"));
        Set<String> perms = permissionService.getUserPermCodes(8L);
        assertEquals(4, perms.size());
        assertTrue(perms.contains("req:list"));
        assertFalse(perms.contains("req:create"));
    }

    @Test
    void qaMgrShouldHaveBaselineLock() {
        when(roleMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of("QA_MGR"));
        when(permissionMapper.selectPermCodesByRoleCodes(anySet()))
            .thenReturn(List.of("baseline:lock", "baseline:unlock", "audit:read", "esign:sign"));
        Set<String> perms = permissionService.getUserPermCodes(2L);
        assertTrue(perms.contains("baseline:lock"));
        assertTrue(perms.contains("esign:sign"));
    }

    @Test
    void reShouldNotHaveBaselineLock() {
        when(roleMapper.selectRoleCodesByUserId(4L)).thenReturn(List.of("RE"));
        when(permissionMapper.selectPermCodesByRoleCodes(anySet()))
            .thenReturn(List.of("req:list", "req:create", "trace:create"));
        Set<String> perms = permissionService.getUserPermCodes(4L);
        assertFalse(perms.contains("baseline:lock"));
        assertFalse(perms.contains("esign:sign"));
        assertTrue(perms.contains("req:create"));
    }

    @Test
    void adminHasPermissionShouldReturnTrueForAnything() {
        when(roleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));
        assertTrue(permissionService.hasPermission(1L, "sys:user:list"));
        assertTrue(permissionService.hasPermission(1L, "baseline:lock"));
        assertTrue(permissionService.hasPermission(1L, "esign:sign"));
    }

    @Test
    void viewerHasPermissionShouldReturnTrueOnlyForReadPerms() {
        when(roleMapper.selectRoleCodesByUserId(8L)).thenReturn(List.of("VIEWER"));
        when(permissionMapper.selectPermCodesByRoleCodes(anySet()))
            .thenReturn(List.of("req:list", "trace:matrix", "esign:read"));
        assertTrue(permissionService.hasPermission(8L, "req:list"));
        assertTrue(permissionService.hasPermission(8L, "trace:matrix"));
        assertTrue(permissionService.hasPermission(8L, "esign:read"));
        assertFalse(permissionService.hasPermission(8L, "req:create"));
        assertFalse(permissionService.hasPermission(8L, "baseline:lock"));
    }

    @Test
    void userWithoutRolesShouldHaveNoPermissions() {
        when(roleMapper.selectRoleCodesByUserId(99L)).thenReturn(List.of());
        assertEquals(Set.of(), permissionService.getUserPermCodes(99L));
        assertFalse(permissionService.hasPermission(99L, "req:list"));
    }

    @Test
    void nullUserShouldReturnFalse() {
        assertFalse(permissionService.hasPermission(null, "req:list"));
        assertFalse(permissionService.hasPermission(1L, null));
    }

    @Test
    void isAdminShouldDetectAdminRole() {
        when(roleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));
        when(roleMapper.selectRoleCodesByUserId(8L)).thenReturn(List.of("VIEWER"));
        assertTrue(permissionService.isAdmin(1L));
        assertFalse(permissionService.isAdmin(8L));
    }
}
