package com.zhutao.medrms.esignature.service;

import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.esignature.domain.entity.SignatureSettings;
import com.zhutao.medrms.esignature.mapper.SignatureSettingsMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SignatureSettingsService 单元测试（W2-D6）
 * 覆盖：getSettings/更新密码/启用 OTP/生成 OTP 密钥/禁用 OTP/更新 PIN/密码验证/OTP 验证/OTP URI
 */
@ExtendWith(MockitoExtension.class)
class SignatureSettingsServiceTest {

    @Mock private SignatureSettingsMapper settingsMapper;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private SignatureSettingsService service;

    // ============================================================
    // 1. getSettings
    // ============================================================

    @Test
    @DisplayName("getSettings-不存在返回默认（userId 已设）")
    void getSettings_empty() {
        when(settingsMapper.selectByUserId(100L)).thenReturn(null);

        SignatureSettings s = service.getSettings(100L);

        assertEquals(100L, s.getUserId());
    }

    @Test
    @DisplayName("getSettings-存在返回原对象")
    void getSettings_exists() {
        SignatureSettings s = new SignatureSettings();
        s.setUserId(100L);
        s.setOtpEnabled(true);
        when(settingsMapper.selectByUserId(100L)).thenReturn(s);

        assertSame(s, service.getSettings(100L));
    }

    // ============================================================
    // 2. updateSignaturePassword
    // ============================================================

    @Test
    @DisplayName("updateSignaturePassword-当前密码错抛 SG0101")
    void updatePassword_wrongCurrent() {
        SignatureSettings s = new SignatureSettings();
        s.setUserId(100L);
        s.setSignaturePasswordHash("$2a$10$OLD_HASH");
        when(settingsMapper.selectByUserId(100L)).thenReturn(s);
        when(passwordEncoder.matches("wrong", "$2a$10$OLD_HASH")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.updateSignaturePassword(100L, "wrong", "new"));
        assertEquals("SG0101", ex.getCode());
    }

    @Test
    @DisplayName("updateSignaturePassword-正确旧密码 → 写新 hash")
    void updatePassword_ok() {
        SignatureSettings s = new SignatureSettings();
        s.setUserId(100L);
        s.setSignaturePasswordHash("$2a$10$OLD_HASH");
        when(settingsMapper.selectByUserId(100L)).thenReturn(s);
        when(passwordEncoder.matches("old", "$2a$10$OLD_HASH")).thenReturn(true);
        when(passwordEncoder.encode("new")).thenReturn("$2a$10$NEW_HASH");

        SignatureSettings result = service.updateSignaturePassword(100L, "old", "new");

        assertEquals("$2a$10$NEW_HASH", result.getSignaturePasswordHash());
        // W20 修复: getSettings 返回 id=null 时改用 insert
        verify(settingsMapper).insert(any(SignatureSettings.class));
    }

    // ============================================================
    // 3. OTP 相关
    // ============================================================

    @Test
    @DisplayName("enableOtp-启用 + 设密钥")
    void enableOtp() {
        SignatureSettings s = new SignatureSettings();
        s.setUserId(100L);
        when(settingsMapper.selectByUserId(100L)).thenReturn(s);

        SignatureSettings result = service.enableOtp(100L, "SECRET123");

        assertEquals("SECRET123", result.getOtpSecret());
        assertTrue(result.getOtpEnabled());
    }

    @Test
    @DisplayName("generateOtpSecret-返回非空密钥 + 落库")
    void generateOtpSecret() {
        SignatureSettings s = new SignatureSettings();
        s.setUserId(100L);
        when(settingsMapper.selectByUserId(100L)).thenReturn(s);

        String secret = service.generateOtpSecret(100L);

        assertNotNull(secret);
        assertFalse(secret.isEmpty());
        assertEquals(secret, s.getOtpSecret());
    }

    @Test
    @DisplayName("disableOtp-清密钥 + 禁用")
    void disableOtp() {
        SignatureSettings s = new SignatureSettings();
        s.setUserId(100L);
        s.setOtpEnabled(true);
        s.setOtpSecret("OLD");
        when(settingsMapper.selectByUserId(100L)).thenReturn(s);

        SignatureSettings result = service.disableOtp(100L);

        assertNull(result.getOtpSecret());
        assertFalse(result.getOtpEnabled());
    }

    // ============================================================
    // 4. PIN
    // ============================================================

    @Test
    @DisplayName("updatePin-写 PIN 哈希 + 启用")
    void updatePin() {
        SignatureSettings s = new SignatureSettings();
        s.setUserId(100L);
        when(settingsMapper.selectByUserId(100L)).thenReturn(s);
        when(passwordEncoder.encode("1234")).thenReturn("$2a$10$PIN_HASH");

        SignatureSettings result = service.updatePin(100L, "1234");

        assertEquals("$2a$10$PIN_HASH", result.getPinHash());
        assertTrue(result.getPinEnabled());
    }

    // ============================================================
    // 5. verifySignaturePassword
    // ============================================================

    @Test
    @DisplayName("verifySignaturePassword-无设置返回 false")
    void verifyPassword_noSettings() {
        when(settingsMapper.selectByUserId(100L)).thenReturn(null);
        assertFalse(service.verifySignaturePassword(100L, "pwd"));
    }

    @Test
    @DisplayName("verifySignaturePassword-无 hash 返回 false")
    void verifyPassword_noHash() {
        SignatureSettings s = new SignatureSettings();
        s.setUserId(100L);
        when(settingsMapper.selectByUserId(100L)).thenReturn(s);

        assertFalse(service.verifySignaturePassword(100L, "pwd"));
    }

    @Test
    @DisplayName("verifySignaturePassword-正确返回 true")
    void verifyPassword_match() {
        SignatureSettings s = new SignatureSettings();
        s.setUserId(100L);
        s.setSignaturePasswordHash("$2a$10$HASH");
        when(settingsMapper.selectByUserId(100L)).thenReturn(s);
        when(passwordEncoder.matches("pwd", "$2a$10$HASH")).thenReturn(true);

        assertTrue(service.verifySignaturePassword(100L, "pwd"));
    }

    // ============================================================
    // 6. verifyOtp
    // ============================================================

    @Test
    @DisplayName("verifyOtp-无设置/未启用/无密钥 → false")
    void verifyOtp_disabled() {
        when(settingsMapper.selectByUserId(100L)).thenReturn(null);
        assertFalse(service.verifyOtp(100L, "123456"));

        SignatureSettings s1 = new SignatureSettings();
        s1.setUserId(100L);
        s1.setOtpEnabled(false);
        when(settingsMapper.selectByUserId(100L)).thenReturn(s1);
        assertFalse(service.verifyOtp(100L, "123456"));

        SignatureSettings s2 = new SignatureSettings();
        s2.setUserId(100L);
        s2.setOtpEnabled(true);
        s2.setOtpSecret(null);
        when(settingsMapper.selectByUserId(100L)).thenReturn(s2);
        assertFalse(service.verifyOtp(100L, "123456"));
    }

    // ============================================================
    // 7. generateOtpUri
    // ============================================================

    @Test
    @DisplayName("generateOtpUri-无密钥抛 SG0102")
    void generateOtpUri_noSecret() {
        SignatureSettings s = new SignatureSettings();
        s.setUserId(100L);
        s.setOtpSecret(null);
        when(settingsMapper.selectByUserId(100L)).thenReturn(s);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.generateOtpUri(100L, "alice"));
        assertEquals("SG0102", ex.getCode());
    }

    @Test
    @DisplayName("generateOtpUri-正常返回 otpauth URI")
    void generateOtpUri_ok() {
        SignatureSettings s = new SignatureSettings();
        s.setUserId(100L);
        s.setOtpSecret("JBSWY3DPEHPK3PXP");
        when(settingsMapper.selectByUserId(100L)).thenReturn(s);

        String uri = service.generateOtpUri(100L, "alice");

        assertTrue(uri.startsWith("otpauth://totp/alice"));
        assertTrue(uri.contains("JBSWY3DPEHPK3PXP"));
    }

    @Test
    @DisplayName("generateOtpUri-空 account 用默认 user-{id}")
    void generateOtpUri_defaultAccount() {
        SignatureSettings s = new SignatureSettings();
        s.setUserId(100L);
        s.setOtpSecret("JBSWY3DPEHPK3PXP");
        when(settingsMapper.selectByUserId(100L)).thenReturn(s);

        String uri = service.generateOtpUri(100L, null);

        assertTrue(uri.contains("user-100"));
    }
}
