package com.zhutao.medrms.admin.controller;

import com.zhutao.medrms.admin.domain.entity.User;
import com.zhutao.medrms.admin.security.RequiresPermission;
import com.zhutao.medrms.admin.service.JwtService;
import com.zhutao.medrms.admin.service.PermissionService;
import com.zhutao.medrms.admin.service.UserService;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.result.Result;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Tag(name = "认证", description = "登录、登出、刷新令牌")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final PermissionService permissionService;
    // R92 修复：原 login 端点不写 AuditLog，导致 /system/login-logs 前端 fallback 后无数据
    // admin 不依赖 compliance，避免循环依赖；直接用 JdbcTemplate 写 audit_log 表
    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "用户登录（返回 access + refresh 双令牌）")
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpReq) {
        User user = userService.authenticate(request.getUsername(), request.getPassword());
        String access = jwtService.generateAccessToken(user);
        String refresh = jwtService.generateRefreshToken(user);
        List<String> roleCodes = permissionService.getUserRoleCodes(user.getId());
        Set<String> permCodes = permissionService.getUserPermCodes(user.getId());
        // R92 修复：写一条 LOGIN 审计日志（前端 LoginLogs.vue 通过 /compliance/audit-logs?eventType=LOGIN 展示）
        try {
            String ip = clientIp(httpReq);
            String ua = httpReq.getHeader("User-Agent");
            int n = jdbcTemplate.update(
                "INSERT INTO compliance_schema.t_audit_log (entity_type, entity_id, operation, operator_id, operator_name, ip_address, user_agent, is_deleted) " +
                "VALUES ('USER', ?, 'LOGIN', ?, ?, ?, ?, false)",
                user.getId(), user.getId(), user.getRealName() != null ? user.getRealName() : user.getUsername(), ip, ua);
            System.out.println("[R92] 写入 LOGIN 审计成功: rows=" + n);
        } catch (Exception e) {
            System.err.println("[R92] 写入 LOGIN 审计失败: " + e.getMessage());
            e.printStackTrace();
        }
        return Result.success(new LoginResponse(
            access, access, refresh, jwtService.getAccessExpirationMs(), jwtService.getRefreshExpirationMs(),
            user.getId(), user.getUsername(), user.getRealName(),
            user.getRole(), roleCodes, permCodes));
    }

    /** 提取客户端 IP（考虑反向代理） */
    private String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = req.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty()) return ip;
        return req.getRemoteAddr();
    }

    @Operation(summary = "刷新 access 令牌（使用 refresh token）")
    @PostMapping("/refresh")
    public Result<RefreshResponse> refresh(@RequestBody RefreshRequest request) {
        String refresh = request.getRefreshToken();
        if (refresh == null || refresh.isEmpty()) {
            throw new BusinessException("AU0100", "缺少 refreshToken");
        }
        if (jwtService.isBlacklisted(refresh)) {
            throw new BusinessException("AU0101", "refresh token 已失效");
        }
        if (!JwtService.TOKEN_TYPE_REFRESH.equals(jwtService.extractTokenType(refresh))) {
            throw new BusinessException("AU0102", "非 refresh token");
        }
        if (jwtService.isTokenExpired(refresh)) {
            throw new BusinessException("AU0103", "refresh token 已过期");
        }
        Long userId = jwtService.extractUserId(refresh);
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("AU0104", "用户不存在");
        }
        String newAccess = jwtService.generateAccessToken(user);
        return Result.success(new RefreshResponse(newAccess, jwtService.getAccessExpirationMs()));
    }

    @Operation(summary = "登出（access 加入黑名单）")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            jwtService.blacklist(token);
        }
        return Result.success();
    }

    @Operation(summary = "检查当前用户是否拥有指定权限码（前端按钮级鉴权用）")
    @GetMapping("/has-perm")
    public Result<Boolean> hasPerm(@RequestParam String code) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return Result.success(false);
        }
        Long userId = (Long) auth.getPrincipal();
        return Result.success(permissionService.hasPermission(userId, code));
    }

    @Operation(summary = "管理员演示 endpoint（@RequiresPermission 演示）")
    @GetMapping("/admin-demo")
    @RequiresPermission("sys:user:list")
    public Result<String> adminDemo() {
        return Result.success("admin-only endpoint accessed OK");
    }

    @lombok.Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @lombok.Data
    public static class RefreshRequest {
        private String refreshToken;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class LoginResponse {
        /** 兼容字段：等价于 accessToken（前端 Login.vue 用 data.token） */
        private String token;
        private String accessToken;
        private String refreshToken;
        private Long accessExpiresMs;
        private Long refreshExpiresMs;
        private Long userId;
        private String username;
        private String realName;
        private String role;
        private List<String> roles;
        private Set<String> permissions;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RefreshResponse {
        private String accessToken;
        private Long accessExpiresMs;
    }
}
