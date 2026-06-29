package com.zhutao.medrms.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        User user = userMapper.selectByUsername(username);
        if (user == null || user.getIsDeleted()) {
            throw BusinessException.unauthorized("用户名或密码错误");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw BusinessException.unauthorized("用户名或密码错误");
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            throw BusinessException.forbidden("用户状态不允许登录");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("用户登录成功: username={}", username);
        return user;
    }

    public List<User> findUsers(String department, String role, String status) {
        log.info("findUsers called: department={}, role={}, status={}", department, role, status);
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
        user.setPasswordHash(passwordEncoder.encode("123456"));
        user.setStatus("ACTIVE");
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.insert(user);
        log.info("创建用户: username={}", user.getUsername());
        return user;
    }

    @Transactional
    public User updateUser(Long id, User updates) {
        User user = getUserById(id);
        if (updates.getRealName() != null) user.setRealName(updates.getRealName());
        if (updates.getEmail() != null) user.setEmail(updates.getEmail());
        if (updates.getPhone() != null) user.setPhone(updates.getPhone());
        if (updates.getDepartment() != null) user.setDepartment(updates.getDepartment());
        if (updates.getRole() != null) user.setRole(updates.getRole());
        if (updates.getStatus() != null) user.setStatus(updates.getStatus());
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
        user.setPasswordHash(passwordEncoder.encode("123456"));
        userMapper.updateById(user);
        log.info("重置密码: id={}", id);
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