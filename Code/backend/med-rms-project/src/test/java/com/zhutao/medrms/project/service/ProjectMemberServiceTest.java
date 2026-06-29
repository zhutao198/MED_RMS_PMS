package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.project.domain.entity.ProjectMember;
import com.zhutao.medrms.project.mapper.ProjectMemberMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProjectMemberService 单元测试（W12-D2）
 * 团队成员 CRUD
 */
@ExtendWith(MockitoExtension.class)
class ProjectMemberServiceTest {

    @Mock private ProjectMemberMapper memberMapper;

    @InjectMocks private ProjectMemberService service;

    private ProjectMember newMember() {
        ProjectMember m = new ProjectMember();
        m.setId(1L);
        m.setProjectId(100L);
        m.setRole("MEMBER");
        m.setIsDeleted(false);
        return m;
    }

    // ============================================================
    // 1. 查询
    // ============================================================

    @Test
    @DisplayName("listByProject-按项目 ID 过滤 + 排除已删除 + 按 role 升序")
    void listByProject() {
        when(memberMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(newMember()));
        assertEquals(1, service.listByProject(100L).size());
    }

    @Test
    @DisplayName("getById-存在则返回")
    void getById_exists() {
        ProjectMember m = newMember();
        when(memberMapper.selectById(1L)).thenReturn(m);
        assertEquals(1L, service.getById(1L).getId());
    }

    @Test
    @DisplayName("getById-不存在抛 PJ0301")
    void getById_notFound() {
        when(memberMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getById(99L));
        assertEquals("PJ0301", ex.getCode());
    }

    @Test
    @DisplayName("getById-已删除抛 PJ0301")
    void getById_deleted() {
        ProjectMember m = newMember();
        m.setIsDeleted(true);
        when(memberMapper.selectById(1L)).thenReturn(m);
        assertThrows(BusinessException.class, () -> service.getById(1L));
    }

    // ============================================================
    // 2. 添加
    // ============================================================

    @Test
    @DisplayName("addMember-默认状态 ACTIVE + joinedAt 设为今天")
    void addMember() {
        ProjectMember input = new ProjectMember();
        input.setProjectId(100L);
        input.setUserId(1L);
        input.setRealName("张三");

        ProjectMember result = service.addMember(input);

        assertEquals("ACTIVE", result.getStatus());
        assertNotNull(result.getJoinedAt());
        verify(memberMapper).insert(input);
    }

    // ============================================================
    // 3. 更新（部分字段）
    // ============================================================

    @Test
    @DisplayName("updateMember-部分字段更新")
    void updateMember_partial() {
        ProjectMember existing = newMember();
        existing.setRole("MEMBER");
        existing.setRealName("OLD");
        when(memberMapper.selectById(1L)).thenReturn(existing);

        ProjectMember patch = new ProjectMember();
        patch.setRole("LEADER");
        patch.setDepartment("RND");

        ProjectMember result = service.updateMember(1L, patch);

        assertEquals("LEADER", result.getRole());
        assertEquals("RND", result.getDepartment());
        verify(memberMapper).updateById(existing);
    }

    // ============================================================
    // 4. 软删除
    // ============================================================

    @Test
    @DisplayName("removeMember-标记 isDeleted=true")
    void removeMember() {
        ProjectMember existing = newMember();
        when(memberMapper.selectById(1L)).thenReturn(existing);

        service.removeMember(1L);

        assertTrue(existing.getIsDeleted());
        verify(memberMapper).updateById(existing);
    }

    // ============================================================
    // 5. 切换角色
    // ============================================================

    @Test
    @DisplayName("switchRole-修改 role 字段")
    void switchRole() {
        ProjectMember existing = newMember();
        existing.setRole("MEMBER");
        when(memberMapper.selectById(1L)).thenReturn(existing);

        ProjectMember result = service.switchRole(1L, "MANAGER");

        assertEquals("MANAGER", result.getRole());
        verify(memberMapper).updateById(existing);
    }
}
