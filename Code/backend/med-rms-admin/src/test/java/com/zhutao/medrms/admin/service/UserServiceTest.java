package com.zhutao.medrms.admin.service;

import com.zhutao.medrms.admin.domain.entity.User;
import com.zhutao.medrms.admin.mapper.UserMapper;
import com.zhutao.medrms.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UserService 单元测试（W12-D1）
 * 覆盖：查询 / 认证 / 列表过滤 / 创建 / 更新 / 软删除 / 重置密码 / 签名密码
 * 关键点：v1.42 BUG 修复（参数校验 + softDeleteById 强制走显式 SQL）
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private UserService service;

    // 构造一个标准的、未删除的 ACTIVE 用户，避免 UserService 内部 getIsDeleted() NPE
    private User newActiveUser() {
        User u = new User();
        u.setId(1L);
        u.setUsername("admin");
        u.setRealName("管理员");
        u.setPasswordHash("$2a$10$hash");
        u.setStatus("ACTIVE");
        u.setIsDeleted(false);
        return u;
    }

    // ============================================================
    // 1. 查询
    // ============================================================

    @Test
    @DisplayName("getUserById-存在则返回")
    void getUserById_exists() {
        User u = newActiveUser();
        when(userMapper.selectById(1L)).thenReturn(u);
        assertEquals(1L, service.getUserById(1L).getId());
    }

    @Test
    @DisplayName("getUserById-不存在抛 SYS0301")
    void getUserById_notFound() {
        when(userMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getUserById(99L));
        assertEquals("SYS0301", ex.getCode());
    }

    @Test
    @DisplayName("getUserById-已删除抛 SYS0301")
    void getUserById_deleted() {
        User u = newActiveUser();
        u.setIsDeleted(true);
        when(userMapper.selectById(1L)).thenReturn(u);
        assertThrows(BusinessException.class, () -> service.getUserById(1L));
    }

    @Test
    @DisplayName("getUserByUsername-存在则返回")
    void getUserByUsername_exists() {
        User u = newActiveUser();
        when(userMapper.selectByUsername("admin")).thenReturn(u);
        assertEquals("admin", service.getUserByUsername("admin").getUsername());
    }

    // ============================================================
    // 2. 认证
    // ============================================================

    @Test
    @DisplayName("authenticate-成功更新 lastLoginAt")
    void authenticate_ok() {
        User u = newActiveUser();
        when(userMapper.selectByUsername("admin")).thenReturn(u);
        when(passwordEncoder.matches("admin123", "$2a$10$hash")).thenReturn(true);

        User result = service.authenticate("admin", "admin123");

        assertNotNull(result.getLastLoginAt());
        verify(userMapper).updateById(u);
    }

    @Test
    @DisplayName("authenticate-密码错抛未授权")
    void authenticate_wrongPassword() {
        User u = newActiveUser();
        when(userMapper.selectByUsername("admin")).thenReturn(u);
        when(passwordEncoder.matches("wrong", "$2a$10$hash")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.authenticate("admin", "wrong"));
        assertEquals("SY0201", ex.getCode());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("authenticate-状态非ACTIVE抛 SY0202")
    void authenticate_disabled() {
        User u = newActiveUser();
        u.setStatus("DISABLED");
        when(userMapper.selectByUsername("admin")).thenReturn(u);
        when(passwordEncoder.matches("admin123", "$2a$10$hash")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.authenticate("admin", "admin123"));
        assertEquals("SY0202", ex.getCode());
    }

    // ============================================================
    // 3. 列表过滤
    // ============================================================

    @Test
    @DisplayName("findUsers-三条件透传到 selectList")
    void findUsers_withFilters() {
        when(userMapper.selectList(any())).thenReturn(List.of(new User(), new User()));
        List<User> result = service.findUsers("RND", "PM", "ACTIVE");
        assertEquals(2, result.size());
        verify(userMapper).selectList(any());
    }

    @Test
    @DisplayName("findUsers-全 null 返回所有未删除")
    void findUsers_noFilters() {
        when(userMapper.selectList(any())).thenReturn(List.of(new User()));
        assertEquals(1, service.findUsers(null, null, null).size());
    }

    // ============================================================
    // 4. 创建（v1.42 BUG 修复：参数校验 + 唯一性）
    // ============================================================

    @Test
    @DisplayName("createUser-成功：密码默认 123456 + 状态 ACTIVE")
    void createUser_success() {
        when(userMapper.selectByUsername("alice")).thenReturn(null);
        when(passwordEncoder.encode("123456")).thenReturn("$2a$10$newHash");

        User input = new User();
        input.setUsername("alice");
        input.setRealName("张三");

        User result = service.createUser(input);

        assertEquals("ACTIVE", result.getStatus());
        assertEquals("$2a$10$newHash", result.getPasswordHash());
        assertNotNull(result.getLastLoginAt());
        verify(userMapper).insert(input);
    }

    @Test
    @DisplayName("createUser-用户名已存在抛 SYS0101")
    void createUser_duplicate() {
        User existing = newActiveUser();
        existing.setUsername("dup");
        when(userMapper.selectByUsername("dup")).thenReturn(existing);

        User input = new User();
        input.setUsername("dup");
        input.setRealName("李四");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createUser(input));
        assertEquals("SY0101", ex.getCode());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("createUser-姓名为空抛 SYS0101")
    void createUser_blankRealName() {
        User input = new User();
        input.setUsername("u1");
        input.setRealName(" ");
        assertThrows(BusinessException.class, () -> service.createUser(input));
    }

    // ============================================================
    // 5. 更新
    // ============================================================

    @Test
    @DisplayName("updateUser-部分字段更新")
    void updateUser_partial() {
        User existing = newActiveUser();
        existing.setRealName("OLD");
        existing.setEmail("old@x.com");
        when(userMapper.selectById(1L)).thenReturn(existing);

        User patch = new User();
        patch.setRealName("NEW");
        patch.setEmail("new@x.com");
        patch.setStatus("ACTIVE");

        User result = service.updateUser(1L, patch);

        assertEquals("NEW", result.getRealName());
        assertEquals("new@x.com", result.getEmail());
        verify(userMapper).updateById(existing);
    }

    // ============================================================
    // 6. 软删除（v1.42 BUG #51：必须走 softDeleteById 显式 SQL）
    // ============================================================

    @Test
    @DisplayName("deleteUser-走 softDeleteById 而非 updateById")
    void deleteUser_usesSoftDelete() {
        User u = newActiveUser();
        when(userMapper.selectById(1L)).thenReturn(u);

        service.deleteUser(1L);

        verify(userMapper).softDeleteById(1L);
        verify(userMapper, never()).updateById(any(User.class));
    }

    // ============================================================
    // 7. 重置密码
    // ============================================================

    @Test
    @DisplayName("resetPassword-重置为 123456")
    void resetPassword() {
        User u = newActiveUser();
        when(userMapper.selectById(1L)).thenReturn(u);
        when(passwordEncoder.encode("123456")).thenReturn("$2a$10$reset");

        service.resetPassword(1L);

        assertEquals("$2a$10$reset", u.getPasswordHash());
        verify(userMapper).updateById(u);
    }

    // ============================================================
    // 8. 签名密码验证
    // ============================================================

    @Test
    @DisplayName("verifySignaturePassword-正确返回 true")
    void verifySignaturePassword_true() {
        User u = newActiveUser();
        u.setSignaturePasswordHash("$2a$10$sig");
        when(userMapper.selectById(1L)).thenReturn(u);
        when(passwordEncoder.matches("sig-pwd", "$2a$10$sig")).thenReturn(true);

        assertTrue(service.verifySignaturePassword(1L, "sig-pwd"));
    }

    @Test
    @DisplayName("verifySignaturePassword-未设置签名密码抛 SYS0101")
    void verifySignaturePassword_notSet() {
        User u = newActiveUser();
        u.setSignaturePasswordHash(null);
        when(userMapper.selectById(1L)).thenReturn(u);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifySignaturePassword(1L, "any"));
        assertEquals("SY0101", ex.getCode());
    }
}
