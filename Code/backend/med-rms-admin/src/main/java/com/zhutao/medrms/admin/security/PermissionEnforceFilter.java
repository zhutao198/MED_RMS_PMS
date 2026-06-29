package com.zhutao.medrms.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhutao.medrms.common.result.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局 RBAC 强制过滤器（v1.27 R28）
 * 位置：在 JwtAuthenticationFilter 之后
 * 逻辑：URL 命中 PermissionMatrix → 取所需 perm → 校验当前用户 authorities 是否含
 *       放行条件：1) 未登录 → 401   2) ADMIN 通配 → 放行   3) 包含 perm → 放行
 *       否则 403 SY0401
 */
@Slf4j
@RequiredArgsConstructor
public class PermissionEnforceFilter extends OncePerRequestFilter {

    private final PermissionMatrix permissionMatrix;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> WHITELIST = Set.of(
        "/auth/login",
        "/auth/has-perm",
        "/v3/api-docs",
        "/swagger-ui",
        "/actuator",
        "/error"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // 1. 白名单放行
        if (isWhitelisted(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 2. 解析所需 perm
        String requiredPerm = permissionMatrix.resolve(method, path);
        if (requiredPerm == null) {
            // 未在矩阵中：放行（登录后即可访问）
            chain.doFilter(request, response);
            return;
        }

        // 3. 检查登录态
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            writeForbidden(response, "SY0401", "未登录或会话已过期");
            return;
        }

        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        Set<String> userPerms = authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> !a.startsWith("ROLE_"))  // 排除 ROLE_*
            .collect(Collectors.toSet());

        // 4. 校验权限
        if (hasPerm(userPerms, requiredPerm)) {
            chain.doFilter(request, response);
        } else {
            log.warn("RBAC 拒绝: user={} path={} method={} required={} has={}",
                auth.getPrincipal(), path, method, requiredPerm, userPerms);
            writeForbidden(response, "SY0401", "无权限：" + requiredPerm);
        }
    }

    private boolean isWhitelisted(String path) {
        for (String w : WHITELIST) {
            if (pathMatcher.matchStart(w, path) || path.startsWith(w)) {
                return true;
            }
        }
        return false;
    }

    /** 权限码匹配：精确 / 模块通配 / 资源通配 */
    private boolean hasPerm(Set<String> userPerms, String required) {
        if (userPerms.contains("*")) return true;
        if (userPerms.contains(required)) return true;
        // 模块通配：req:*  →  req:list
        int idx = required.indexOf(':');
        if (idx > 0) {
            String moduleWild = required.substring(0, idx) + ":*";
            if (userPerms.contains(moduleWild)) return true;
            // 资源通配：req:list:* → req:list:foo
            int idx2 = required.indexOf(':', idx + 1);
            if (idx2 > 0) {
                String resWild = required.substring(0, idx2) + ":*";
                if (userPerms.contains(resWild)) return true;
            }
        }
        return false;
    }

    private void writeForbidden(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> body = Result.error(403, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
