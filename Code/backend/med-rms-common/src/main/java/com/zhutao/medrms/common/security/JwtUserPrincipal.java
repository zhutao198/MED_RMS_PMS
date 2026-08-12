package com.zhutao.medrms.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * JWT 认证 principal（公共安全契约，供 web / admin / compliance 等模块共用）。
 * - {@link #getName()} / {@link #getUsername()} 返回登录用户名（username），
 *   确保审计日志 operator_name、电子签名 signer_name 取到真实姓名而非 userId。
 * - {@link #getUserId()} 返回数字 ID，供 Service 层按 ID 取数。
 *
 * P1-8 / P2-4 修复：原实现直接把 userId(Long) 作为 principal，
 * 导致 auth.getName() 返回 userId 字符串，审计 operator_name 与实际不符。
 */
public class JwtUserPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final String realName;
    private final Collection<? extends GrantedAuthority> authorities;

    public JwtUserPrincipal(Long userId, String username, String realName,
                            Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.realName = realName;
        this.authorities = authorities;
    }

    public Long getUserId() {
        return userId;
    }

    /** 真实姓名（realName 优先，缺失时回退 username） */
    public String getRealName() {
        if (realName != null && !realName.isBlank()) {
            return realName;
        }
        return username;
    }

    @Override
    public String getUsername() {
        return username;
    }

    /** 审计/电子签名取 signer 名称时统一走这里 */
    public String getName() {
        return username;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
