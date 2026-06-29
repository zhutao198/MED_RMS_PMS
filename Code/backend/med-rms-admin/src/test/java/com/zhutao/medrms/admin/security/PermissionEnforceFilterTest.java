package com.zhutao.medrms.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PermissionEnforceFilter 单元测试（v1.27 R28）
 * 覆盖 6 类核心场景：白名单 / 未匹配 / admin 通配 / 精确匹配 / 403 / 未登录
 */
class PermissionEnforceFilterTest {

    private PermissionMatrix matrix;
    private PermissionEnforceFilter filter;

    @BeforeEach
    void setup() {
        matrix = new PermissionMatrix();
        filter = new PermissionEnforceFilter(matrix);
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPassWhitelist_login() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, resp, chain);
        verify(chain).doFilter(req, resp);
        assertEquals(200, resp.getStatus());
    }

    @Test
    void shouldPassWhitelist_swagger() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v3/api-docs/swagger-config");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, resp, chain);
        verify(chain).doFilter(req, resp);
    }

    @Test
    void shouldPassThroughUnmatchedPath() throws Exception {
        // 未在矩阵中的路径：放行（即使登录后也能访问）
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/some/unknown/path");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        setAuth(1L, Set.of("req:list"));
        filter.doFilter(req, resp, chain);
        verify(chain).doFilter(req, resp);
    }

    @Test
    void shouldPassAdminWildcard() throws Exception {
        // admin 用户：通配 * → 全部放行
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/requirements");
        req.setRequestURI("/api/requirements");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        setAuth(1L, Set.of("*"));
        filter.doFilter(req, resp, chain);
        verify(chain).doFilter(req, resp);
        assertEquals(200, resp.getStatus());
    }

    @Test
    void shouldPassCorrectPerm() throws Exception {
        // PM 角色：req:create 应被允许
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/requirements");
        req.setRequestURI("/api/requirements");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        setAuth(2L, Set.of("req:create", "req:list"));
        filter.doFilter(req, resp, chain);
        verify(chain).doFilter(req, resp);
        assertEquals(200, resp.getStatus());
    }

    @Test
    void shouldDeny403WhenMissingPerm() throws Exception {
        // VIEWER 角色尝试 POST /requirements（需要 req:create）→ 403
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/requirements");
        req.setRequestURI("/api/requirements");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        setAuth(11L, Set.of("req:list", "trace:list", "chg:list"));
        filter.doFilter(req, resp, chain);
        verify(chain, never()).doFilter(req, resp);
        assertEquals(403, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("SY0401") || resp.getContentAsString().contains("无权限"));
    }

    @Test
    void shouldDeny403WhenAccessingSystemUsersAsViewer() throws Exception {
        // VIEWER 尝试 /system/users（需要 sys:user:list）→ 403
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/system/users");
        req.setRequestURI("/api/system/users");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        setAuth(11L, Set.of("req:list", "proj:list"));
        filter.doFilter(req, resp, chain);
        verify(chain, never()).doFilter(req, resp);
        assertEquals(403, resp.getStatus());
    }

    @Test
    void shouldDeny403WhenNotAuthenticated() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/requirements");
        req.setRequestURI("/api/requirements");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        // 不设置 SecurityContext
        filter.doFilter(req, resp, chain);
        verify(chain, never()).doFilter(req, resp);
        assertEquals(403, resp.getStatus());
    }

    @Test
    void shouldPassModuleWildcard() throws Exception {
        // 拥有 req:* 通配的，应能通过所有 req:* 端点
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/requirements");
        req.setRequestURI("/api/requirements");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        setAuth(3L, Set.of("req:*"));
        try {
            filter.doFilter(req, resp, chain);
        } catch (Exception e) {
            fail("Filter should not throw: " + e.getMessage());
        }
        verify(chain).doFilter(req, resp);
    }

    @Test
    void shouldEnforceOnChangeApprove() throws Exception {
        // REVIEWER 尝试 POST /changes/{id}/approve（需要 chg:approve）→ 403
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/changes/1/approve");
        req.setRequestURI("/api/changes/1/approve");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        setAuth(5L, Set.of("req:review", "esign:sign"));
        filter.doFilter(req, resp, chain);
        verify(chain, never()).doFilter(req, resp);
        assertEquals(403, resp.getStatus());
    }

    @Test
    void shouldAllowPM_ChangeApprove() throws Exception {
        // PM 拥有 chg:approve → 放行
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/changes/1/approve");
        req.setRequestURI("/api/changes/1/approve");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        setAuth(3L, Set.of("chg:approve", "chg:create"));
        filter.doFilter(req, resp, chain);
        verify(chain).doFilter(req, resp);
    }

    private void setAuth(Long userId, Set<String> perms) {
        var authorities = perms.stream()
            .map(SimpleGrantedAuthority::new)
            .map(a -> (org.springframework.security.core.GrantedAuthority) a)
            .toList();
        var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
