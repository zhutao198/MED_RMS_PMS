package com.zhutao.medrms.web.config.security;

import com.zhutao.medrms.admin.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);
            if (StringUtils.hasText(jwt)
                    && !jwtService.isTokenExpired(jwt)
                    && !jwtService.isBlacklisted(jwt)) {
                Claims claims = jwtService.parseToken(jwt);
                Long userId = Long.valueOf(claims.getSubject());

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                // 角色作为 ROLE_*
                Object rolesObj = claims.get("roles");
                if (rolesObj instanceof java.util.Collection<?> roles) {
                    for (Object r : roles) {
                        if (r != null) authorities.add(new SimpleGrantedAuthority("ROLE_" + r));
                    }
                } else {
                    String role = claims.get("role", String.class);
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + (role != null ? role : "USER")));
                }
                // 权限码作为 authority（@PreAuthorize("hasAuthority('xxx')")）
                Set<String> perms = jwtService.extractPermissions(claims);
                for (String p : perms) {
                    // "*" 也作为 authority 注入，由 PermissionEnforceFilter 识别为 admin 通配
                    authorities.add(new SimpleGrantedAuthority(p));
                }

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            log.debug("JWT authentication failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
