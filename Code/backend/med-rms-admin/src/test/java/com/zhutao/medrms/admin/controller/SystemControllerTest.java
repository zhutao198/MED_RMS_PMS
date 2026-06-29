package com.zhutao.medrms.admin.controller;

import com.zhutao.medrms.admin.domain.entity.User;
import com.zhutao.medrms.admin.service.UserService;
import com.zhutao.medrms.common.result.Result;
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
 * SystemController 单元测试（v1.27 R28）
 * 覆盖用户列表/详情/创建/更新/删除/重置密码
 */
@ExtendWith(MockitoExtension.class)
class SystemControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private SystemController controller;

    @Test
    void getUsers_returnsList() {
        User u1 = new User(); u1.setId(1L); u1.setUsername("admin");
        User u2 = new User(); u2.setId(2L); u2.setUsername("qa_mgr");
        when(userService.findUsers(null, null, null)).thenReturn(Arrays.asList(u1, u2));

        Result<List<User>> result = controller.getUsers(null, null, null);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().size());
    }

    @Test
    void getUser_returnsDetail() {
        User u = new User(); u.setId(1L); u.setUsername("admin");
        when(userService.getUserById(1L)).thenReturn(u);

        Result<User> result = controller.getUser(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("admin", result.getData().getUsername());
    }

    @Test
    void createUser_returnsNewUser() {
        User input = new User(); input.setUsername("newuser");
        User saved = new User(); saved.setId(99L); saved.setUsername("newuser");
        when(userService.createUser(any())).thenReturn(saved);

        Result<User> result = controller.createUser(input);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(99L, result.getData().getId());
    }

    @Test
    void updateUser_returnsUpdated() {
        User updates = new User(); updates.setEmail("a@b.c");
        when(userService.updateUser(eq(1L), any())).thenReturn(updates);

        Result<User> result = controller.updateUser(1L, updates);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(userService, times(1)).updateUser(eq(1L), any());
    }

    @Test
    void deleteUser_returnsSuccess() {
        Result<Void> result = controller.deleteUser(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(userService, times(1)).deleteUser(1L);
    }

    @Test
    void resetPassword_returnsSuccess() {
        Result<Void> result = controller.resetPassword(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(userService, times(1)).resetPassword(1L);
    }
}
