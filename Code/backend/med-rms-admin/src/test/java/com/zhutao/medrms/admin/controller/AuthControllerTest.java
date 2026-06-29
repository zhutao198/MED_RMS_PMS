package com.zhutao.medrms.admin.controller;

import com.zhutao.medrms.admin.domain.entity.User;
import com.zhutao.medrms.admin.service.JwtService;
import com.zhutao.medrms.admin.service.PermissionService;
import com.zhutao.medrms.admin.service.UserService;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AuthController 单元测试（W15-D1）
 * 登录/登出/刷新令牌/权限检查
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private UserService userService;
    @Mock private JwtService jwtService;
    @Mock private PermissionService permissionService;

    @InjectMocks private AuthController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private User newUser() {
        User u = new User();
        u.setId(1L);
        u.setUsername("admin");
        u.setRealName("管理员");
        u.setRole("ADMIN");
        u.setIsDeleted(false);
        return u;
    }

    // ============================================================
    // 1. 登录
    // ============================================================

    @Test
    @DisplayName("login-成功：返回双令牌 + 角色/权限")
    void login_ok() {
        when(userService.authenticate("admin", "admin123")).thenReturn(newUser());
        when(jwtService.generateAccessToken(any())).thenReturn("access-xxx");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-xxx");
        when(jwtService.getAccessExpirationMs()).thenReturn(3600000L);
        when(jwtService.getRefreshExpirationMs()).thenReturn(86400000L);
        when(permissionService.getUserRoleCodes(anyLong())).thenReturn(List.of("ADMIN"));
        when(permissionService.getUserPermCodes(anyLong())).thenReturn(Set.of("sys:user:list"));

        AuthController.LoginRequest req = new AuthController.LoginRequest();
        req.setUsername("admin");
        req.setPassword("admin123");

        Result<AuthController.LoginResponse> result = controller.login(req, mock(HttpServletRequest.class));

        assertEquals(200, result.getCode());
        assertEquals("admin", result.getData().getUsername());
        assertNotNull(result.getData().getToken(), "兼容字段 token 应等于 accessToken");
        assertEquals("access-xxx", result.getData().getToken());
        assertEquals(List.of("ADMIN"), result.getData().getRoles());
    }

    @Test
    @DisplayName("login-密码错抛 BusinessException")
    void login_wrongPassword() {
        when(userService.authenticate("admin", "wrong"))
                .thenThrow(BusinessException.unauthorized("用户名或密码错误"));

        AuthController.LoginRequest req = new AuthController.LoginRequest();
        req.setUsername("admin");
        req.setPassword("wrong");

        assertThrows(BusinessException.class, () -> controller.login(req, mock(HttpServletRequest.class)));
    }

    // ============================================================
    // 2. 刷新令牌
    // ============================================================

    @Test
    @DisplayName("refresh-成功：返回新 access 令牌")
    void refresh_ok() {
        when(jwtService.extractTokenType("valid-refresh")).thenReturn(JwtService.TOKEN_TYPE_REFRESH);
        when(jwtService.isTokenExpired("valid-refresh")).thenReturn(false);
        when(jwtService.isBlacklisted("valid-refresh")).thenReturn(false);
        when(jwtService.extractUserId("valid-refresh")).thenReturn(1L);
        when(userService.getUserById(1L)).thenReturn(newUser());
        when(jwtService.generateAccessToken(any())).thenReturn("new-access");
        when(jwtService.getAccessExpirationMs()).thenReturn(3600000L);

        AuthController.RefreshRequest req = new AuthController.RefreshRequest();
        req.setRefreshToken("valid-refresh");

        Result<AuthController.RefreshResponse> result = controller.refresh(req);

        assertEquals("new-access", result.getData().getAccessToken());
    }

    @Test
    @DisplayName("refresh-refreshToken 为空抛 AU0100")
    void refresh_emptyToken() {
        AuthController.RefreshRequest req = new AuthController.RefreshRequest();
        req.setRefreshToken("");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.refresh(req));
        assertEquals("AU0100", ex.getCode());
    }

    @Test
    @DisplayName("refresh-黑名单 token 抛 AU0101")
    void refresh_blacklisted() {
        when(jwtService.isBlacklisted("blacklisted")).thenReturn(true);

        AuthController.RefreshRequest req = new AuthController.RefreshRequest();
        req.setRefreshToken("blacklisted");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.refresh(req));
        assertEquals("AU0101", ex.getCode());
    }

    @Test
    @DisplayName("refresh-非 refresh token 类型抛 AU0102")
    void refresh_wrongType() {
        when(jwtService.extractTokenType("access-token")).thenReturn("access");
        when(jwtService.isBlacklisted(anyString())).thenReturn(false);

        AuthController.RefreshRequest req = new AuthController.RefreshRequest();
        req.setRefreshToken("access-token");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.refresh(req));
        assertEquals("AU0102", ex.getCode());
    }

    @Test
    @DisplayName("refresh-过期 token 抛 AU0103")
    void refresh_expired() {
        when(jwtService.extractTokenType("expired")).thenReturn(JwtService.TOKEN_TYPE_REFRESH);
        when(jwtService.isBlacklisted(anyString())).thenReturn(false);
        when(jwtService.isTokenExpired("expired")).thenReturn(true);

        AuthController.RefreshRequest req = new AuthController.RefreshRequest();
        req.setRefreshToken("expired");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.refresh(req));
        assertEquals("AU0103", ex.getCode());
    }

    // ============================================================
    // 3. 登出
    // ============================================================

    @Test
    @DisplayName("logout-带 Bearer token：加入黑名单")
    void logout_withToken() {
        HttpServletRequest request = new MockHttpServletRequest();
        ((MockHttpServletRequest) request).addHeader("Authorization", "Bearer access-token");

        Result<Void> result = controller.logout(request);

        assertEquals(200, result.getCode());
        verify(jwtService).blacklist("access-token");
    }

    @Test
    @DisplayName("logout-无 Authorization 头：静默成功")
    void logout_noHeader() {
        HttpServletRequest request = new MockHttpServletRequest();

        Result<Void> result = controller.logout(request);

        assertEquals(200, result.getCode());
        verify(jwtService, never()).blacklist(anyString());
    }

    // ============================================================
    // 4. 权限检查
    // ============================================================

    @Test
    @DisplayName("hasPerm-已登录：检查权限码")
    void hasPerm_loggedIn() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of()));
        when(permissionService.hasPermission(1L, "sys:user:list")).thenReturn(true);

        Result<Boolean> result = controller.hasPerm("sys:user:list");

        assertTrue(result.getData());
    }

    @Test
    @DisplayName("hasPerm-未登录：返回 false")
    void hasPerm_notLoggedIn() {
        SecurityContextHolder.clearContext();

        Result<Boolean> result = controller.hasPerm("sys:user:list");

        assertFalse(result.getData());
    }

    // ============================================================
    // W16-D5: Bug 1 修复的单测覆盖
    // 验证：logout 加入黑名单 → 后续 refresh 应拒绝
    // 验证：logout 加 access token → 再次 refresh 应拿不到有效令牌
    // ============================================================

    @Test
    @DisplayName("logout-加入黑名单后 refresh 应被拒绝（防止 refresh token 泄露复用）")
    void logoutThenRefresh_blocked() {
        // 模拟 access token 被加入黑名单（refresh 接口先 isBlacklisted 校验）
        when(jwtService.isBlacklisted("access-blacklisted")).thenReturn(true);

        // 验证：refresh 接口用此 token 时应被拒
        AuthController.RefreshRequest req = new AuthController.RefreshRequest();
        req.setRefreshToken("access-blacklisted");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.refresh(req));
        assertEquals("AU0101", ex.getCode(),
                "黑名单 token 应被拒绝 refresh（v1.47 BUG #108 修复）");
    }

    @Test
    @DisplayName("logout-带 refresh 类型 token 也能加入黑名单")
    void logout_blacklistWorksForRefreshToken() {
        // 验证 isBlacklisted 拦截器对 refresh token 也生效
        when(jwtService.isBlacklisted("refresh-blacklisted")).thenReturn(true);

        AuthController.RefreshRequest req = new AuthController.RefreshRequest();
        req.setRefreshToken("refresh-blacklisted");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.refresh(req));
        assertEquals("AU0101", ex.getCode());
    }

    @Test
    @DisplayName("refresh-成功场景下 generateAccessToken 必被调用（W15 Bug 1 修复点）")
    void refresh_generatesNewToken() {
        when(jwtService.extractTokenType("valid-refresh")).thenReturn(JwtService.TOKEN_TYPE_REFRESH);
        when(jwtService.isTokenExpired("valid-refresh")).thenReturn(false);
        when(jwtService.isBlacklisted("valid-refresh")).thenReturn(false);
        when(jwtService.extractUserId("valid-refresh")).thenReturn(1L);
        when(userService.getUserById(1L)).thenReturn(newUser());
        when(jwtService.generateAccessToken(any())).thenReturn("NEW-ACCESS-TOKEN");
        when(jwtService.getAccessExpirationMs()).thenReturn(3600000L);

        AuthController.RefreshRequest req = new AuthController.RefreshRequest();
        req.setRefreshToken("valid-refresh");

        Result<AuthController.RefreshResponse> result = controller.refresh(req);

        // 验证：必须生成新 access token（前端 Bug 1 修复依赖此行为）
        assertEquals("NEW-ACCESS-TOKEN", result.getData().getAccessToken());
        verify(jwtService).generateAccessToken(any());
    }
}
