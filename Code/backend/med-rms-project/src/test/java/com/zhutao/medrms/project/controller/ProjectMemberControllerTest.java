package com.zhutao.medrms.project.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.project.domain.entity.ProjectMember;
import com.zhutao.medrms.project.service.ProjectMemberService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ProjectMemberController 单元测试（v1.27 R28）
 * 覆盖项目成员列表/详情/添加/更新/删除/角色切换
 */
@ExtendWith(MockitoExtension.class)
class ProjectMemberControllerTest {

    @Mock
    private ProjectMemberService memberService;

    @InjectMocks
    private ProjectMemberController controller;

    @Test
    void listByProject_returnsMembers() {
        ProjectMember m1 = new ProjectMember(); m1.setId(1L); m1.setRole("DEV");
        ProjectMember m2 = new ProjectMember(); m2.setId(2L); m2.setRole("QA");
        when(memberService.listByProject(1L)).thenReturn(Arrays.asList(m1, m2));

        Result<List<ProjectMember>> result = controller.listByProject(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().size());
    }

    @Test
    void getById_returnsMember() {
        ProjectMember m = new ProjectMember(); m.setId(1L); m.setRole("DEV");
        when(memberService.getById(1L)).thenReturn(m);

        Result<ProjectMember> result = controller.getById(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("DEV", result.getData().getRole());
    }

    @Test
    void addMember_returnsNew() {
        ProjectMember input = new ProjectMember(); input.setUserId(10L); input.setRole("DEV");
        ProjectMember saved = new ProjectMember(); saved.setId(99L); saved.setUserId(10L);
        when(memberService.addMember(any())).thenReturn(saved);

        Result<ProjectMember> result = controller.addMember(input);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(99L, result.getData().getId());
    }

    @Test
    void updateMember_returnsUpdated() {
        ProjectMember updates = new ProjectMember(); updates.setRole("QA");
        when(memberService.updateMember(eq(1L), any())).thenReturn(updates);

        Result<ProjectMember> result = controller.updateMember(1L, updates);

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void removeMember_returnsSuccess() {
        Result<Void> result = controller.removeMember(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(memberService, times(1)).removeMember(1L);
    }

    @Test
    void switchRole_returnsNewRole() {
        ProjectMember m = new ProjectMember(); m.setId(1L); m.setRole("QA");
        when(memberService.switchRole(eq(1L), eq("QA"))).thenReturn(m);

        Result<ProjectMember> result = controller.switchRole(1L, "QA");

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("QA", result.getData().getRole());
    }
}
