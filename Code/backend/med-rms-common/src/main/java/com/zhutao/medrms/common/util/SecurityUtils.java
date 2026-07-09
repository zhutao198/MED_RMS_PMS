package com.zhutao.medrms.common.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.UUID;

@Slf4j
public class SecurityUtils {

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private SecurityUtils() {}

    /**
     * R157 修复（F1）：跨模块审计哈希链并发控制锁
     * AuthController（admin 模块）和 AuditLogService（compliance 模块）共用此锁，
     * 确保 getLastHash() + calculateAuditHash() 原子执行，杜绝并发竞态。
     */
    public static final Object AUDIT_HASH_LOCK = new Object();

    // R162: RSA 2048 密钥对（21 CFR Part 11 §11.70 防篡改签名）
    private static final KeyPair RSA_KEY_PAIR;

    static {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048, new SecureRandom());
            RSA_KEY_PAIR = gen.generateKeyPair();
            log.info("RSA 密钥对初始化完成（2048位）");
        } catch (NoSuchAlgorithmException e) {
            log.error("RSA 算法不可用", e);
            throw new RuntimeException("RSA 算法不可用", e);
        }
    }

    /**
     * R162: RSA-SHA256 签名（21 CFR Part 11 §11.70）
     * @return Base64 编码的签名值
     */
    public static String rsaSign(String data) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(RSA_KEY_PAIR.getPrivate());
            sig.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig.sign());
        } catch (Exception e) {
            log.error("RSA 签名失败", e);
            throw new RuntimeException("RSA 签名失败", e);
        }
    }

    /**
     * R162: RSA-SHA256 验签
     * @param data 原始数据
     * @param signatureBase64 Base64 编码的签名值
     * @return 是否匹配
     */
    public static boolean rsaVerify(String data, String signatureBase64) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(RSA_KEY_PAIR.getPublic());
            sig.update(data.getBytes(StandardCharsets.UTF_8));
            return sig.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            log.error("RSA 验签失败", e);
            return false;
        }
    }

    /**
     * 生成UUID
     */
    public static String generateUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成签名ID (UUID格式)
     */
    public static String generateSignatureId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * SHA-256哈希
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not found", e);
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * 计算实体哈希
     */
    public static String calculateEntityHash(String entityType, Long entityId, Object entityContent) {
        String contentJson = toJson(entityContent);
        String input = entityType + entityId + contentJson;
        return sha256(input);
    }

    /**
     * R162: 计算 RSA-SHA256 签名值（21 CFR Part 11 §11.70）
     * 从 SHA-256 升级为 RSA-SHA256，满足 PRD 签名值计算规则
     */
    public static String calculateSignatureValue(Long signerId, String meaning, String signedAt, String entityHash) {
        String input = signerId + "|" + meaning + "|" + signedAt + "|" + entityHash;
        return rsaSign(input);
    }

    /**
     * 计算审计日志哈希
     * v1.45 BUG #93 修复：去掉内部 toJson，由调用方负责传 JSON 字符串。
     * 修复前：recordAuditLog 内部已经 setOldValue(toJson(oldValue))，再传 toJson 后的字符串进 calculateAuditHash 还会被 toJson 一次（双重 JSON）；
     *         verifyHashChain 取出存储的 JSON 字符串再传入，会被 toJson 一次（也是双重 JSON），导致 hash 永远对不上。
     * 修复后：调用方必须传「与存储一致」的字符串值，hash 函数本身不再 toJson。
     * v1.46 字段命名修正：参数 beforeValueJson/afterValueJson → oldValueJson/newValueJson，对应 DDL 139。
     */
    public static String calculateAuditHash(String prevHash, String eventType, String entityType,
                                            Long entityId, Long operatorId, String operation,
                                            String oldValueJson, String newValueJson, String timestamp) {
        // R131 fix: nullToEmpty on all String params
        String content = String.join("|",
                nullToEmpty(prevHash),
                nullToEmpty(eventType),
                nullToEmpty(entityType),
                String.valueOf(entityId),
                String.valueOf(operatorId),
                nullToEmpty(operation),
                oldValueJson == null ? "" : oldValueJson,
                newValueJson == null ? "" : newValueJson,
                nullToEmpty(timestamp));
        return sha256(content);
    }

    /**
     * 将byte数组转换为十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX_DIGITS[(b >> 4) & 0xf]).append(HEX_DIGITS[b & 0xf]);
        }
        return sb.toString();
    }

    private static String toJson(Object obj) {
        if (obj == null) return "";
        return JSON.toJSONString(obj);
    }

    private static String nullToEmpty(String str) {
        return str == null ? "" : str;
    }

    /**
     * 判断字符串是否为空白
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 判断字符串是否为非空白
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * 获取当前登录用户ID（来自 JWT principal）
     * 未登录时返回 null（不抛异常，便于 Service 层兜底）
     */
    public static Long getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getPrincipal() == null) {
                return null;
            }
            Object principal = auth.getPrincipal();
            if (principal instanceof Long l) {
                return l;
            }
            if (principal instanceof Number n) {
                return n.longValue();
            }
            if (principal instanceof String s) {
                try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
            }
            return null;
        } catch (Exception e) {
            log.debug("getCurrentUserId failed: {}", e.getMessage());
            return null;
        }
    }
}
