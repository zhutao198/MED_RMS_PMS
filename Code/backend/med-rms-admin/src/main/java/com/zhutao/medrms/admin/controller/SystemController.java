package com.zhutao.medrms.admin.controller;

import com.zhutao.medrms.admin.domain.dto.OrgNode;
import com.zhutao.medrms.admin.domain.entity.User;
import com.zhutao.medrms.admin.domain.entity.DictItem;
import com.zhutao.medrms.admin.domain.entity.Permission;
import com.zhutao.medrms.admin.domain.entity.Role;
import com.zhutao.medrms.admin.domain.entity.SystemConfig;
import com.zhutao.medrms.admin.service.PermissionService;
import com.zhutao.medrms.admin.service.UserService;
import com.zhutao.medrms.admin.service.SystemService;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.common.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "系统管理", description = "用户、角色、字典、系统配置管理接口")
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemController {

    private final UserService userService;
    private final SystemService systemService;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "获取用户列表")
    @GetMapping("/users")
    public Result<List<User>> getUsers(@RequestParam(required = false) String department,
                                       @RequestParam(required = false) String role,
                                       @RequestParam(required = false) String status) {
        return Result.success(userService.findUsers(department, role, status));
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/users/{id}")
    public Result<User> getUser(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }

    @Operation(summary = "创建用户")
    @PostMapping("/users")
    public Result<User> createUser(@RequestBody User user) {
        return Result.success(userService.createUser(user));
    }

    @Operation(summary = "更新用户")
    @PutMapping("/users/{id}")
    public Result<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        return Result.success(userService.updateUser(id, user));
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }

    // R92 修复：原后端无 /system/profile 端点，前端只能 fallback 到 localStorage → 用户名显示"-"
    @Operation(summary = "获取当前登录用户信息（个人中心）")
    @GetMapping("/profile")
    public Result<User> getCurrentProfile() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("SY0301", "用户未登录");
        }
        return Result.success(userService.getUserById(userId));
    }

    // R92 修复：原后端无此端点（SY0301）。新增：校验旧密码 + BCrypt 更新新密码
    @Operation(summary = "修改密码")
    @PostMapping("/users/{id}/change-password")
    public Result<Void> changePassword(@PathVariable Long id,
                                       @RequestBody Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || newPassword == null || newPassword.length() < 6) {
            throw new BusinessException("RQ0101", "旧密码/新密码不能为空，新密码至少 6 位");
        }
        User user = userService.getUserById(id);
        if (user == null) {
            throw new BusinessException("SY0301", "用户不存在: id=" + id);
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException("RQ0101", "旧密码错误");
        }
        userService.updatePassword(id, passwordEncoder.encode(newPassword));
        return Result.success();
    }

    @Operation(summary = "重置密码")
    @PostMapping("/users/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return Result.success();
    }

    @Operation(summary = "获取角色列表")
    @GetMapping("/roles")
    public Result<List<Role>> getRoles() {
        return Result.success(systemService.getRoles());
    }

    @Operation(summary = "创建角色")
    @PostMapping("/roles")
    public Result<Role> createRole(@RequestBody Role role) {
        return Result.success(systemService.createRole(role));
    }

    @Operation(summary = "更新角色")
    @PutMapping("/roles/{id}")
    public Result<Role> updateRole(@PathVariable Long id, @RequestBody Role role) {
        return Result.success(systemService.updateRole(id, role));
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/roles/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        systemService.deleteRole(id);
        return Result.success();
    }

    @Operation(summary = "获取字典")
    @GetMapping("/dicts")
    public Result<List<DictItem>> getDicts(@RequestParam String type) {
        return Result.success(systemService.getDictsByType(type));
    }

    @Operation(summary = "获取所有字典")
    @GetMapping("/dicts/all")
    public Result<List<DictItem>> getAllDicts() {
        return Result.success(systemService.getAllDicts());
    }

    @Operation(summary = "创建字典项")
    @PostMapping("/dicts")
    public Result<DictItem> createDict(@RequestBody DictItem item) {
        return Result.success(systemService.createDict(item));
    }

    @Operation(summary = "更新字典项")
    @PutMapping("/dicts/{id}")
    public Result<DictItem> updateDict(@PathVariable Long id, @RequestBody DictItem item) {
        return Result.success(systemService.updateDict(id, item));
    }

    @Operation(summary = "删除字典项")
    @DeleteMapping("/dicts/{id}")
    public Result<Void> deleteDict(@PathVariable Long id) {
        systemService.deleteDict(id);
        return Result.success();
    }

    @Operation(summary = "获取系统配置列表")
    @GetMapping("/configs")
    public Result<List<SystemConfig>> getConfigs() {
        return Result.success(systemService.getConfigs());
    }

    @Operation(summary = "更新系统配置")
    @PutMapping("/configs/{id}")
    public Result<SystemConfig> updateConfig(@PathVariable Long id, @RequestBody SystemConfig config) {
        return Result.success(systemService.updateConfig(id, config));
    }

    // v1.42 BUG #49 修复：组织架构树端点（之前未实现，返回 SY0301）
    @Operation(summary = "获取组织架构树")
    @GetMapping("/org/tree")
    public Result<List<OrgNode>> getOrgTree() {
        return Result.success(systemService.getOrgTree());
    }

    // ---------- v1.46 P1-后端-2：角色权限矩阵管理 ----------

    @Operation(summary = "全量权限码（含名称/类型，用于授权界面渲染）")
    @GetMapping("/permissions")
    public Result<List<Permission>> listAllPermissions() {
        return Result.success(permissionService.listAllPermissions());
    }

    @Operation(summary = "角色已授予的权限码集合")
    @GetMapping("/roles/{id}/permissions")
    public Result<List<String>> getRolePermissions(@PathVariable Long id) {
        return Result.success(permissionService.getRolePermCodes(id));
    }

    @Operation(summary = "全量替换角色权限（覆盖更新）")
    @PutMapping("/roles/{id}/permissions")
    public Result<List<String>> updateRolePermissions(@PathVariable Long id,
                                                      @RequestBody Map<String, List<String>> body) {
        permissionService.replaceRolePermissions(id, body.getOrDefault("permCodes", List.of()));
        return Result.success(permissionService.getRolePermCodes(id));
    }
}