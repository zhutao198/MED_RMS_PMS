package com.zhutao.medrms.admin.service;

import com.zhutao.medrms.admin.domain.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtService 集成测试（W11-D2）
 * 使用真实 jjwt 库（HS512），验证 token 生成/解析/字段提取
 * 注意：使用 service 自身的默认密钥（@PostConstruct 注入），保证密钥一致
 */
class JwtServiceIntegrationTest {

    private final PermissionService permissionService;
    private final JwtService service;

    JwtServiceIntegrationTest() {
        this.permissionService = org.mockito.Mockito.mock(PermissionService.class);
        org.mockito.Mockito.when(permissionService.getUserRoleCodes(org.mockito.ArgumentMatchers.anyLong()))
            .thenReturn(java.util.Collections.singletonList("ADMIN"));
        this.service = new JwtService(permissionService);
    }

    @Test
    @DisplayName("generateAccessToken-生成非空 token")
    void generateAccessToken() {
        User u = new User();
        u.setId(1L);
        u.setUsername("admin");
        u.setRole("ADMIN");
        u.setRealName("Administrator");

        String token = service.generateAccessToken(u);

        assertNotNull(token);
        assertTrue(token.length() > 50);
    }

    @Test
    @DisplayName("parseToken-解析生成的 token 拿回 userId/username/role")
    void parseToken() {
        User u = new User();
        u.setId(100L);
        u.setUsername("alice");
        u.setRole("PM");
        u.setRealName("Alice");

        String token = service.generateAccessToken(u);
        Claims claims = service.parseToken(token);

        assertEquals(100L, claims.get("userId", Long.class));
        assertEquals("alice", claims.get("username", String.class));
        assertEquals("PM", claims.get("role", String.class));
        assertEquals("access", claims.get("tokenType", String.class));
    }

    @Test
    @DisplayName("extractUserId/Username/Jti-3 个提取方法")
    void extractMethods() {
        User u = new User();
        u.setId(200L);
        u.setUsername("bob");
        u.setRole("QA");
        u.setRealName("Bob");

        String token = service.generateAccessToken(u);

        assertEquals(200L, service.extractUserId(token));
        assertEquals("bob", service.extractUsername(token));
        assertNotNull(service.extractJti(token));
    }

    @Test
    @DisplayName("generateRefreshToken-tokenType=refresh")
    void generateRefreshToken() {
        User u = new User();
        u.setId(1L);
        u.setUsername("admin");
        u.setRole("ADMIN");
        u.setRealName("Admin");

        String token = service.generateRefreshToken(u);
        Claims claims = service.parseToken(token);

        assertEquals("refresh", claims.get("tokenType", String.class));
    }
}
