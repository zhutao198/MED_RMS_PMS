package com.zhutao.medrms.admin.controller;

import com.zhutao.medrms.admin.domain.entity.User;
import com.zhutao.medrms.admin.service.UserService;
import com.zhutao.medrms.common.result.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminController 单元测试（W14-D1）
 * 用户管理 Controller（v1.46 BUG #99 修复后）
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock private UserService userService;

    @InjectMocks private AdminController controller;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User newUser() {
        User u = new User();
        u.setId(1L);
        u.setUsername("admin");
        u.setRealName("管理员");
        u.setIsDeleted(false);
        return u;
    }

    @Test
    @DisplayName("getCurrentUser-已登录：principal 是 Long 时按 userId 查询")
    void getCurrentUser_loggedIn() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of()));

        when(userService.getUserById(1L)).thenReturn(newUser());

        Result<User> result = controller.getCurrentUser();

        assertEquals(200, result.getCode());
        assertEquals(1L, result.getData().getId());
        verify(userService).getUserById(1L);
    }

    @Test
    @DisplayName("getCurrentUser-未登录：返回错误（AUTH0001 在 errorCode 扩展字段）")
    void getCurrentUser_notLoggedIn() {
        SecurityContextHolder.clearContext();

        Result<User> result = controller.getCurrentUser();

        // Result.error(String code, String message) 把第一个参数作为 errorCode 扩展
        // 主 code 是 500，message 是错误信息
        assertEquals(500, result.getCode());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("未登录"));
    }

    @Test
    @DisplayName("getUser-按 ID 查询")
    void getUser() {
        when(userService.getUserById(1L)).thenReturn(newUser());

        Result<User> result = controller.getUser(1L);

        assertEquals(200, result.getCode());
        assertEquals("admin", result.getData().getUsername());
    }

    @Test
    @DisplayName("test-测试接口")
    void test() {
        Result<String> result = controller.test();

        assertEquals(200, result.getCode());
        assertEquals("test ok", result.getData());
    }

    @Test
    @DisplayName("verifySignaturePassword-正确返回 true")
    void verifySignaturePassword_ok() {
        when(userService.verifySignaturePassword(anyLong(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);

        Result<Boolean> result = controller.verifySignaturePassword(1L, "sig-pwd");

        assertTrue(result.getData());
    }
}
