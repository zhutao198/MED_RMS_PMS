package com.zhutao.medrms.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.admin.domain.entity.Department;
import com.zhutao.medrms.admin.domain.entity.User;
import com.zhutao.medrms.admin.mapper.DepartmentMapper;
import com.zhutao.medrms.admin.mapper.UserMapper;
import com.zhutao.medrms.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * R99 DepartmentService 单测（W32-D1）
 * 覆盖核心 CRUD + 防误删 + 防重复编码场景。
 */
@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock private DepartmentMapper departmentMapper;
    @Mock private UserMapper userMapper;

    @InjectMocks private DepartmentService service;

    @Test
    @DisplayName("createDepartment-顶级部门：level=1，path=/id/")
    void createDepartment_root() {
        when(departmentMapper.selectByCode(any())).thenReturn(null);
        // 模拟 insert 后 id 自动填充为 100（BaseMapper.insert 返回 int）
        doAnswer(inv -> {
            Department d = inv.getArgument(0);
            d.setId(100L);
            return 1;
        }).when(departmentMapper).insert(any(Department.class));

        Department input = new Department();
        input.setName("测试部门");
        input.setCode("TEST");
        input.setParentId(0L);

        Department result = service.createDepartment(input);

        assertEquals(100L, result.getId());
        assertEquals(1, result.getLevel());
        assertEquals("/100/", result.getPath());
    }

    @Test
    @DisplayName("createDepartment-编码重复抛 SY0402")
    void createDepartment_duplicateCode() {
        Department exist = new Department();
        exist.setId(99L);
        when(departmentMapper.selectByCode("DUP")).thenReturn(exist);

        Department input = new Department();
        input.setName("X");
        input.setCode("DUP");

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.createDepartment(input));
        // code 字段 = "SY0402"，不在 message 中
        assertEquals("SY0402", ex.getCode());
    }

    @Test
    @DisplayName("createDepartment-父部门不存在抛 SY0301")
    void createDepartment_parentNotFound() {
        when(departmentMapper.selectByCode(any())).thenReturn(null);
        when(departmentMapper.selectById(999L)).thenReturn(null);

        Department input = new Department();
        input.setName("X");
        input.setCode("X");
        input.setParentId(999L);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.createDepartment(input));
        assertEquals("SY0301", ex.getCode());
    }

    @Test
    @DisplayName("deleteDepartment-有子部门禁止删除 SY0403")
    void deleteDepartment_hasChildren() {
        Department existing = new Department();
        existing.setId(1L);
        existing.setCode("X");
        when(departmentMapper.selectById(1L)).thenReturn(existing);
        when(departmentMapper.selectChildrenByParentId(1L))
            .thenReturn(Arrays.asList(new Department(), new Department()));

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.deleteDepartment(1L));
        assertEquals("SY0403", ex.getCode());
        verify(departmentMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("deleteDepartment-有关联用户禁止删除 SY0404")
    void deleteDepartment_hasUsers() {
        Department existing = new Department();
        existing.setId(1L);
        existing.setCode("X");
        when(departmentMapper.selectById(1L)).thenReturn(existing);
        when(departmentMapper.selectChildrenByParentId(1L)).thenReturn(Collections.emptyList());
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.deleteDepartment(1L));
        assertEquals("SY0404", ex.getCode());
        verify(departmentMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("deleteDepartment-无子无用户可正常删除")
    void deleteDepartment_ok() {
        Department existing = new Department();
        existing.setId(1L);
        existing.setCode("X");
        when(departmentMapper.selectById(1L)).thenReturn(existing);
        when(departmentMapper.selectChildrenByParentId(1L)).thenReturn(Collections.emptyList());
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.deleteDepartment(1L);

        verify(departmentMapper).deleteById(1L);
    }

    @Test
    @DisplayName("updateSortOrder-正常排序更新")
    void updateSortOrder_ok() {
        Department existing = new Department();
        existing.setId(1L);
        when(departmentMapper.selectById(1L)).thenReturn(existing);
        when(departmentMapper.updateById(any(Department.class))).thenReturn(1);

        service.updateSortOrder(1L, 5);

        assertEquals(5, existing.getSortOrder());
        verify(departmentMapper).updateById(existing);
    }
}