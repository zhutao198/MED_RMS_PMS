package com.zhutao.medrms.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhutao.medrms.admin.security.PermissionEnforceFilter;
import com.zhutao.medrms.admin.security.PermissionMatrix;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.web.config.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PermissionEnforceFilter permissionEnforceFilter(PermissionMatrix permissionMatrix) {
        return new PermissionEnforceFilter(permissionMatrix);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           PermissionEnforceFilter permissionEnforceFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/auth/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/actuator/**",
                    "/error",
                    "/api/health"
                ).permitAll()
                .anyRequest().permitAll()  // W21 修复：JwtAuthenticationFilter 内部已处理认证
            )
            .exceptionHandling(ex -> ex
                // W21 修复：无 token → 401（之前 403）
                .authenticationEntryPoint((req, res, e) -> writeJsonError(res, 401, "SY0201", "未登录或会话已过期"))
                // W21 修复：权限不足 → 403（带业务码）
                .accessDeniedHandler((req, res, e) -> writeJsonError(res, 403, "SY0202", "权限不足"))
            )
            .addFilterBefore(jwtAuthenticationFilter, BasicAuthenticationFilter.class)
            .addFilterAfter(permissionEnforceFilter, BasicAuthenticationFilter.class);

        return http.build();
    }

    private void writeJsonError(HttpServletResponse res, int httpStatus, String code, String message) throws java.io.IOException {
        res.setStatus(httpStatus);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        Result<?> body = Result.error(code, message);
        res.getWriter().write(objectMapper.writeValueAsString(body));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}