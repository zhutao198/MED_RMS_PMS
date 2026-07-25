package com.zhutao.medrms.admin.controller;

import com.zhutao.medrms.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * R225.2 CONTRACT-008：登录日志端点（前端 LoginLogs.vue 调用）
 *
 * 设计：复用 compliance_schema.t_audit_log 表中 event_type='LOGIN' / 'LOGOUT' 的记录
 *       通过 JdbcTemplate 跨 schema 查询（admin 模块不直接依赖 compliance 模块）
 *
 * 字段：username / ipAddress / userAgent / status / createdAt
 *       status: SUCCESS / FAILED（基于 audit_log 字段推导）
 */
@Tag(name = "登录日志", description = "登录日志查询接口")
@RestController
@RequestMapping("/system/login-logs")
@RequiredArgsConstructor
public class LoginLogController {

    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "查询登录日志")
    @GetMapping
    public Result<List<Map<String, Object>>> list(
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {
        // R225.2: 直接跨 schema 查 audit_log 表，event_type 过滤 LOGIN/LOGOUT
        // SELECT * 避免列名变更导致 SQL 错误（125 迁移后部分列被重命名）
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM compliance_schema.t_audit_log " +
            "WHERE event_type IN ('LOGIN', 'LOGOUT') " +
            "  AND is_deleted = false");
        Object[] params;
        if (username != null && !username.isBlank()) {
            sql.append(" AND operator_name LIKE ?");
            params = new Object[]{"%" + username + "%", size, page * size};
        } else {
            params = new Object[]{size, page * size};
        }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");

        List<Map<String, Object>> logs = jdbcTemplate.queryForList(sql.toString(), params);
        return Result.success(logs);
    }
}