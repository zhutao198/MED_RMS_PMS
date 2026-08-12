package com.zhutao.medrms.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.admin.domain.dto.UserUpdateRequest;
import com.zhutao.medrms.admin.domain.entity.User;
import com.zhutao.medrms.admin.mapper.UserMapper;
import com.zhutao.medrms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    public User getUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null || user.getIsDeleted()) {
            throw BusinessException.notFound("SYS0301", "用户不存在");
        }
        return user;
    }

    public User getUserByUsername(String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null || user.getIsDeleted()) {
            throw BusinessException.notFound("SYS0301", "用户不存在");
        }
        return user;
    }

    public User authenticate(String username, String password) {
        if (loginAttemptService.isLocked(username)) {
            long remaining = loginAttemptService.getRemainingLockSeconds(username);
            throw BusinessException.forbidden("账号已锁定，请 " + (remaining / 60 + 1) + " 分钟后再试");
        }

        User user = userMapper.selectByUsername(username);
        if (user == null || user.getIsDeleted()) {
            loginAttemptService.recordFailedAttempt(username);
            throw BusinessException.unauthorized("用户名或密码错误");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttemptService.recordFailedAttempt(username);
            throw BusinessException.unauthorized("用户名或密码错误");
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            throw BusinessException.forbidden("用户状态不允许登录");
        }

        loginAttemptService.resetAttempts(username);

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("用户登录成功: username={}", username);
        return user;
    }

    // R277：用户列表缓存（高频调用：5+ 处 Vue 页面，5min TTL）
    private final com.zhutao.medrms.common.util.TimedCache<String, List<User>> userListCache =
            new com.zhutao.medrms.common.util.TimedCache<>(5 * 60 * 1000L);

    public List<User> findUsers(String department, String role, String status) {
        // 仅缓存"无过滤"调用（最常见场景：前端下拉选用户列表）
        boolean cacheable = (department == null || department.isBlank())
                && (role == null || role.isBlank())
                && (status == null || status.isBlank());
        if (cacheable) {
            // TimedCache.get 带 loader 模式：命中直接返回；未命中调 loader 加载 + 自动缓存
            return userListCache.get("all", () -> {
                log.info("findUsers cache miss → DB query");
                return userMapper.selectList(
                    new LambdaQueryWrapper<User>().eq(User::getIsDeleted, false)
                );
            });
        }
        log.info("findUsers no-cache: department={}, role={}, status={}", department, role, status);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getIsDeleted, false);
        if (department != null && !department.isBlank()) {
            wrapper.eq(User::getDepartment, department);
        }
        if (role != null && !role.isBlank()) {
            wrapper.eq(User::getRole, role);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(User::getStatus, status);
        }
        return userMapper.selectList(wrapper);
    }

    // R277：写入操作后清除用户列表缓存（保证一致性）
    public void invalidateUserListCache() {
        userListCache.invalidateAll();
    }

    @Transactional
    public User createUser(User user) {
        // v1.42 BUG 修复：参数校验 + 唯一性校验
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw BusinessException.param("用户名不能为空");
        }
        if (user.getRealName() == null || user.getRealName().isBlank()) {
            throw BusinessException.param("姓名不能为空");
        }
        if (userMapper.selectByUsername(user.getUsername()) != null) {
            throw BusinessException.param("用户名已存在: " + user.getUsername());
        }
        // R226.1 SEC-005：首次登录强制改密（创建时不设置默认密码，标记 PENDING_RESET）
        user.setPasswordHash(passwordEncoder.encode(generateInitialPassword()));
        user.setStatus("PENDING_RESET");
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.insert(user);
        log.info("创建用户（待首次登录改密）: username={}, initialPwd=系统生成", user.getUsername());
        return user;
    }

    @Transactional
    public User updateUser(Long id, UserUpdateRequest updates) {
        // P0-5 修复：仅允许通过 UserUpdateRequest DTO 显式列出的字段被修改；
        // role / status / username / passwordHash 等敏感字段必须走专用接口。
        User user = getUserById(id);
        if (updates.getRealName() != null) user.setRealName(updates.getRealName());
        if (updates.getEmail() != null) user.setEmail(updates.getEmail());
        if (updates.getPhone() != null) user.setPhone(updates.getPhone());
        if (updates.getDepartment() != null) user.setDepartment(updates.getDepartment());
        if (updates.getDeptId() != null) user.setDeptId(updates.getDeptId());
        userMapper.updateById(user);
        log.info("更新用户: id={}", id);
        return user;
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = getUserById(id);
        // v1.42 BUG #51 修复：@TableLogic 字段在 updateById 中被忽略
        // 必须用 @Update 注解的 mapper 方法或直接 SQL 显式更新
        userMapper.softDeleteById(id);
        log.info("删除用户: id={}", id);
    }

    @Transactional
    public void resetPassword(Long id) {
        User user = getUserById(id);
        // R226.1 SEC-005：admin 重置密码时生成随机初始密码（强随机），并标记 PENDING_RESET
        String initialPwd = generateInitialPassword();
        user.setPasswordHash(passwordEncoder.encode(initialPwd));
        user.setStatus("PENDING_RESET");
        userMapper.updateById(user);
        log.info("重置密码（待首次登录改密）: id={}, initialPwd=系统生成", id);
    }

    /**
     * R226.1 SEC-005：生成 12 位强随机初始密码（含大小写+数字+特殊字符）
     * 替代原硬编码 "123456"
     */
    private String generateInitialPassword() {
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$%^&*";
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    /**
     * R226.1 SEC-005：密码复杂度校验（≥8 位，必须含大小写字母+数字）
     */
    public static void validatePasswordComplexity(String password) {
        if (password == null || password.length() < 8) {
            throw BusinessException.param("密码至少 8 位");
        }
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasUpper || !hasLower || !hasDigit) {
            throw BusinessException.param("密码必须同时包含大小写字母和数字");
        }
    }

    // R92 新增：用户自己改密码（带旧密码校验）。区别于 resetPassword（admin 重置为默认密码）
    @Transactional
    public void updatePassword(Long id, String newEncodedPassword) {
        User user = getUserById(id);
        user.setPasswordHash(newEncodedPassword);
        userMapper.updateById(user);
        log.info("用户修改密码: id={}", id);
    }

    public boolean verifySignaturePassword(Long userId, String signaturePassword) {
        User user = getUserById(userId);
        if (user.getSignaturePasswordHash() == null) {
            throw BusinessException.param("用户未设置签名密码");
        }
        return passwordEncoder.matches(signaturePassword, user.getSignaturePasswordHash());
    }
}