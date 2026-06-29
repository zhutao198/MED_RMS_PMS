package com.zhutao.medrms.esignature.service;

import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.esignature.domain.entity.SignatureSettings;
import com.zhutao.medrms.esignature.mapper.SignatureSettingsMapper;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignatureSettingsService {

    private final SignatureSettingsMapper settingsMapper;
    private final PasswordEncoder passwordEncoder;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, new SystemTimeProvider());

    public SignatureSettings getSettings(Long userId) {
        SignatureSettings settings = settingsMapper.selectByUserId(userId);
        if (settings == null) {
            settings = new SignatureSettings();
            settings.setUserId(userId);
            return settings;
        }
        return settings;
    }

    @Transactional
    public SignatureSettings updateSignaturePassword(Long userId, String currentPwd, String newPwd) {
        SignatureSettings settings = getSettings(userId);

        if (currentPwd != null && settings.getSignaturePasswordHash() != null) {
            if (!passwordEncoder.matches(currentPwd, settings.getSignaturePasswordHash())) {
                throw BusinessException.notFound("SG0101", "当前签名密码不正确");
            }
        }

        String hashedNew = passwordEncoder.encode(newPwd);
        settings.setSignaturePasswordHash(hashedNew);
        settings.setUpdatedAt(LocalDateTime.now());
        // W20 修复：getSettings 在记录不存在时返回 id=null 的临时对象，
        // MyBatis-Plus updateById 对 id=null 不执行 INSERT，导致密码永远写不进库。
        // 手动判断：id=null 时用 insert，否则 update。
        if (settings.getId() == null) {
            settingsMapper.insert(settings);
        } else {
            settingsMapper.updateById(settings);
        }

        log.info("更新签名密码: userId={}", userId);
        return settings;
    }

    @Transactional
    public SignatureSettings enableOtp(Long userId, String otpSecret) {
        SignatureSettings settings = getSettings(userId);
        settings.setOtpSecret(otpSecret);
        settings.setOtpEnabled(true);
        settings.setUpdatedAt(LocalDateTime.now());
        settingsMapper.updateById(settings);
        log.info("启用OTP: userId={}", userId);
        return settings;
    }

    @Transactional
    public String generateOtpSecret(Long userId) {
        String secret = secretGenerator.generate();
        SignatureSettings settings = getSettings(userId);
        settings.setOtpSecret(secret);
        settings.setUpdatedAt(LocalDateTime.now());
        settingsMapper.updateById(settings);
        log.info("生成OTP密钥: userId={}", userId);
        return secret;
    }

    @Transactional
    public SignatureSettings disableOtp(Long userId) {
        SignatureSettings settings = getSettings(userId);
        settings.setOtpEnabled(false);
        settings.setOtpSecret(null);
        settings.setUpdatedAt(LocalDateTime.now());
        settingsMapper.updateById(settings);
        log.info("禁用OTP: userId={}", userId);
        return settings;
    }

    @Transactional
    public SignatureSettings updatePin(Long userId, String newPin) {
        SignatureSettings settings = getSettings(userId);
        settings.setPinHash(passwordEncoder.encode(newPin));
        settings.setPinEnabled(true);
        settings.setUpdatedAt(LocalDateTime.now());
        settingsMapper.updateById(settings);
        log.info("更新PIN: userId={}", userId);
        return settings;
    }

    public boolean verifySignaturePassword(Long userId, String password) {
        SignatureSettings settings = getSettings(userId);
        if (settings == null || settings.getSignaturePasswordHash() == null) {
            return false;
        }
        return passwordEncoder.matches(password, settings.getSignaturePasswordHash());
    }

    public boolean verifyOtp(Long userId, String otpCode) {
        SignatureSettings settings = getSettings(userId);
        if (settings == null || !settings.getOtpEnabled() || settings.getOtpSecret() == null) {
            return false;
        }
        try {
            return codeVerifier.isValidCode(settings.getOtpSecret(), otpCode);
        } catch (Exception e) {
            log.warn("OTP验证失败: userId={}, error={}", userId, e.getMessage());
            return false;
        }
    }

    public String generateOtpUri(Long userId, String account) {
        SignatureSettings settings = getSettings(userId);
        if (settings.getOtpSecret() == null) {
            throw BusinessException.notFound("SG0102", "OTP密钥未设置");
        }
        return String.format("otpauth://totp/%s?secret=%s&issuer=MedRMS",
                account != null ? account : "user-" + userId,
                settings.getOtpSecret());
    }
}