package com.zhutao.medrms.admin.controller;

import com.zhutao.medrms.admin.domain.entity.User;
import com.zhutao.medrms.admin.service.UserService;
import com.zhutao.medrms.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "系统管理", description = "用户、角色、权限管理")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final UserService userService;

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/users/me")
    public Result<User> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null) {
            // v1.46 BUG #99 修复：JWT filter 把 userId (Long) 作为 principal，
            // auth.getName() 返回 "1" 而不是用户名。直接拿 principal 当 userId 查询。
            Object principal = auth.getPrincipal();
            Long userId = principal instanceof Long l ? l : Long.valueOf(auth.getName());
            User user = userService.getUserById(userId);
            return Result.success(user);
        }
        return Result.error("AUTH0001", "未登录或登录已过期");
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/users/{id}")
    public Result<User> getUser(@PathVariable("id") Long id) {
        log.info("getUser called with id={}, type={}", id, id == null ? "null" : id.getClass().getName());
        try {
            User user = userService.getUserById(id);
            log.info("getUser got user: {}", user);
            return Result.success(user);
        } catch (Exception e) {
            log.error("getUser failed", e);
            return Result.error("SY0000", "获取用户失败: " + e.getMessage());
        }
    }

    @Operation(summary = "测试接口")
    @GetMapping("/test")
    public Result<String> test() {
        log.info("test called");
        return Result.success("test ok");
    }

    @Operation(summary = "验证签名密码")
    @PostMapping("/users/{id}/verify-signature-password")
    public Result<Boolean> verifySignaturePassword(
            @PathVariable Long id,
            @RequestParam String signaturePassword) {
        return Result.success(userService.verifySignaturePassword(id, signaturePassword));
    }
}