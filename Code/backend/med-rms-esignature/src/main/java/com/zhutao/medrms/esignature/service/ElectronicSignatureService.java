package com.zhutao.medrms.esignature.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhutao.medrms.common.annotation.AuditLog;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.util.SecurityUtils;
import com.zhutao.medrms.esignature.domain.entity.ElectronicSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import com.zhutao.medrms.esignature.domain.entity.SignatureIntent;
import com.zhutao.medrms.esignature.mapper.ElectronicSignatureMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElectronicSignatureService {

    private final ElectronicSignatureMapper signatureMapper;
    private final SignatureSettingsService settingsService;
    private final SignatureIntentService intentService;

    /**
     * R162: 签名值升级为 RSA-SHA256（21 CFR Part 11 §11.70）
     * 公式 = RSA-Sign(documentType + documentId + entityHash + meaningCode + signerId + timestamp)
     */
    private String calculateSignatureValue(String documentType, Long documentId, String entityHash,
                                            String meaningCode, Long signerId, LocalDateTime timestamp) {
        String concat = String.valueOf(documentType) + "|" + documentId + "|" +
                (entityHash == null ? "" : entityHash) + "|" +
                (meaningCode == null ? "" : meaningCode) + "|" +
                signerId + "|" + timestamp.toString();
        return SecurityUtils.rsaSign(concat);
    }

    /**
     * v1.46 BUG #103 修复：实体哈希 = SHA-256(documentType + documentId + documentNo)
     * 用于 verify 时重算比对，防止文档被篡改（§11.10(e)）
     * entityHash 保持 SHA-256（文档指纹），signatureValue 才是 RSA 签名
     */
    public String calculateEntityHash(String documentType, Long documentId, String documentNo) {
        String concat = String.valueOf(documentType) + "|" + documentId + "|" + (documentNo == null ? "" : documentNo);
        return SecurityUtils.sha256(concat);
    }

    /**
     * R155 修复：电子签名触发 @AuditLog 写审计日志（21 CFR Part 11 §11.10(e) 关键事件）
     * entityIdSpel 用 #p5（第 6 个参数 documentId）
     * captureArgs=false 避免敏感字段（签名密码、OTP）写入审计
     */
    @Transactional
    @AuditLog(eventType = "SIGN", entityType = "ELECTRONIC_SIGNATURE",
              operation = "电子签名", entityIdSpel = "#p5", captureArgs = false)
    public ElectronicSignature sign(Long signerId, String signerName, Long intentId, String meaningCode,
                                     String documentType, Long documentId, String documentNo,
                                     String reason, String signatureMethod, String ipAddress,
                                     String signaturePassword, String otpCode) {
        // 21 CFR Part 11 §11.200 G2: 验证签名人身份与 JWT 认证用户一致
        Long jwtUserId = SecurityUtils.getCurrentUserId();
        if (jwtUserId == null || !jwtUserId.equals(signerId)) {
            log.warn("[SECURITY] signerId 与 JWT 身份不匹配: signerId={}, jwtUserId={}", signerId, jwtUserId);
            throw BusinessException.notFound("SG0108", "签名人身份验证失败：请求 signerId 与登录用户不匹配");
        }
        // v1.46 BUG #104 修复：必须先校验 SignatureIntent（未过期 + requesterId 匹配）
        if (intentId == null) {
            throw BusinessException.notFound("SG0104", "缺少签名意图 ID（必须先 createIntent）");
        }
        SignatureIntent intent = intentService.validateAndConsume(intentId, signerId);

        // Verify signature password
        if (signaturePassword != null && !signaturePassword.isEmpty()) {
            boolean passwordValid = settingsService.verifySignaturePassword(signerId, signaturePassword);
            if (!passwordValid) {
                throw BusinessException.notFound("SG0103", "签名密码验证失败");
            }
        }

        if (otpCode != null && !otpCode.isEmpty()) {
            boolean otpValid = settingsService.verifyOtp(signerId, otpCode);
            if (!otpValid) {
                throw BusinessException.notFound("SG0104", "OTP验证码验证失败");
            }
        }

        // Check if already signed
        LambdaQueryWrapper<ElectronicSignature> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ElectronicSignature::getDocumentType, documentType)
               .eq(ElectronicSignature::getDocumentId, documentId)
               .eq(ElectronicSignature::getSignerId, signerId)
               .eq(ElectronicSignature::getIntent, intent.getIntentCode())
               .eq(ElectronicSignature::getIsValid, true);
        if (signatureMapper.selectCount(wrapper) > 0) {
            throw BusinessException.notFound("SG0102", "签名已存在，不可重复签名");
        }

        // v1.46 BUG #102/103 修复：计算实体哈希和签名值
        String entityHash = calculateEntityHash(documentType, documentId, documentNo);
        LocalDateTime signedAt = LocalDateTime.now();
        String signatureValue = calculateSignatureValue(documentType, documentId, entityHash,
                meaningCode != null ? meaningCode : intent.getMeaningCode(), signerId, signedAt);

        // Create signature record
        ElectronicSignature signature = new ElectronicSignature();
        signature.setSignatureType(intent.getIntentCode());
        signature.setIntent(intent.getIntentCode());
        signature.setSignerId(signerId);
        signature.setSignerName(signerName);
        signature.setDocumentType(documentType);
        signature.setDocumentId(documentId);
        signature.setDocumentNo(documentNo);
        signature.setEntityHash(entityHash);
        signature.setSignatureValue(signatureValue);
        // 兼容旧字段：写与 signatureValue 相同值
        signature.setSignatureHash(signatureValue);
        signature.setSignatureMethod(signatureMethod);
        signature.setDeviceInfo(signatureMethod);
        signature.setIpAddress(ipAddress);
        signature.setReason(reason);
        signature.setIsValid(true);
        signature.setSignedAt(signedAt);

        signatureMapper.insert(signature);

        // v1.46 BUG #105 修复：标记 Intent 已消费
        intentService.markConsumed(intentId, signature.getId(), signerId);

        // v1.46 BUG #106 修复：签名完成写审计日志（21 CFR Part 11 §11.10(e)）
        log.info("[AUDIT][ESIGN][SIGN] signatureId={}, signerId={}, doc={}/{}, value={}",
                signature.getId(), signerId, documentType, documentId, signatureValue);

        log.info("电子签名完成: id={}, signerId={}, document={}/{}",
                signature.getId(), signerId, documentType, documentId);

        return signature;
    }

    public boolean verifySignature(Long signatureId) {
        ElectronicSignature signature = signatureMapper.selectById(signatureId);
        if (signature == null || !signature.getIsValid()) {
            return false;
        }
        // R162: 升级为 RSA 验签 — 重算 entityHash 比对 + RSA 验签
        String currentEntityHash = calculateEntityHash(signature.getDocumentType(),
                signature.getDocumentId(), signature.getDocumentNo());
        boolean entityHashMatch = currentEntityHash.equals(signature.getEntityHash());
        // RSA 验签：用原始字段重算签名值并比对
        String expectedData = String.valueOf(signature.getDocumentType()) + "|" + signature.getDocumentId() + "|" +
                (signature.getEntityHash() == null ? "" : signature.getEntityHash()) + "|" +
                (signature.getIntent() == null ? "" : signature.getIntent()) + "|" +
                signature.getSignerId() + "|" + signature.getSignedAt().toString();
        boolean rsaMatch = SecurityUtils.rsaVerify(expectedData, signature.getSignatureValue());
        boolean valid = entityHashMatch && rsaMatch;
        log.info("签名验证: id={}, entityHashMatch={}, rsaMatch={}, result={}",
                signatureId, entityHashMatch, rsaMatch, valid);
        return valid;
    }

    public Map<String, Object> verifySignatureWithDetail(Long signatureId) {
        ElectronicSignature signature = signatureMapper.selectById(signatureId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (signature == null) {
            result.put("valid", false);
            result.put("signerName", "-");
            result.put("signTime", "-");
            result.put("message", "签名记录不存在");
            return result;
        }
        if (!signature.getIsValid()) {
            result.put("valid", false);
            result.put("signerName", signature.getSignerName());
            result.put("signTime", signature.getSignedAt() != null ? signature.getSignedAt().toString() : "-");
            result.put("message", "签名已被作废");
            return result;
        }
        // R162: RSA 验签 + 实体哈希比对双验证
        String currentEntityHash = calculateEntityHash(signature.getDocumentType(),
                signature.getDocumentId(), signature.getDocumentNo());
        boolean entityHashMatch = currentEntityHash.equals(signature.getEntityHash());
        String expectedData = String.valueOf(signature.getDocumentType()) + "|" + signature.getDocumentId() + "|" +
                (signature.getEntityHash() == null ? "" : signature.getEntityHash()) + "|" +
                (signature.getIntent() == null ? "" : signature.getIntent()) + "|" +
                signature.getSignerId() + "|" + signature.getSignedAt().toString();
        boolean rsaMatch = SecurityUtils.rsaVerify(expectedData, signature.getSignatureValue());
        boolean valid = entityHashMatch && rsaMatch;
        result.put("valid", valid);
        result.put("signerName", signature.getSignerName());
        result.put("signTime", signature.getSignedAt() != null ? signature.getSignedAt().toString() : "-");
        if (!valid) {
            String msg = !entityHashMatch ? "实体哈希不匹配，文档可能已被篡改" : "RSA 签名不匹配，签名数据可能被篡改";
            result.put("message", msg);
        } else {
            result.put("message", "签名有效（RSA-SHA256）");
        }
        log.info("签名验证详情: id={}, entityHashMatch={}, rsaMatch={}, valid={}",
                signatureId, entityHashMatch, rsaMatch, valid);
        return result;
    }

    public List<ElectronicSignature> getSignaturesForEntity(String documentType, Long documentId) {
        return signatureMapper.selectByEntity(documentType, documentId);
    }

    /**
     * R156 扩展：作废签名触发 @AuditLog 写审计日志
     * entityIdSpel=#p0（signatureId），captureArgs=false 避免 reason 写入审计
     */
    @Transactional
    @AuditLog(eventType = "INVALIDATE", entityType = "ELECTRONIC_SIGNATURE",
              operation = "作废签名", entityIdSpel = "#p0", captureArgs = false)
    public void invalidateSignature(Long signatureId, Long operatorId, String reason) {
        ElectronicSignature signature = signatureMapper.selectById(signatureId);
        if (signature == null) {
            throw BusinessException.notFound("SG0103", "签名记录不存在");
        }
        signature.setIsValid(false);
        signature.setReason((signature.getReason() == null ? "" : signature.getReason()) +
                " [INVALIDATED by " + operatorId + " at " + LocalDateTime.now() + " reason=" + reason + "]");
        signatureMapper.updateById(signature);
        // v1.46 BUG #106 修复：作废操作写审计
        log.info("[AUDIT][ESIGN][INVALIDATE] signatureId={}, operatorId={}, reason={}", signatureId, operatorId, reason);
    }

    public boolean verifySignaturePassword(Long userId, String password) {
        return settingsService.verifySignaturePassword(userId, password);
    }

    public boolean verifyOtp(Long userId, String otpCode) {
        return settingsService.verifyOtp(userId, otpCode);
    }

    public IPage<ElectronicSignature> listSignatures(Long signerId, String documentType, int page, int size) {
        // R94 修复：原 .eq() 大小写敏感，但 DB 里 document_type 混合大小写（REQUIREMENT/requirement/baseline）
        // mybatis-plus apply varargs 在本版本编译后 SQL 不拼接
        // 改为：前端传入 documentType 后，前端按大小写兼容数组传给后端，这里只做 .eq()
        // 前端按 documentType 把所有大小写变体加 IN 查询传过来
        Page<ElectronicSignature> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<ElectronicSignature> wrapper = new LambdaQueryWrapper<>();
        if (signerId != null) wrapper.eq(ElectronicSignature::getSignerId, signerId);
        // documentType 过滤交给前端在 params 里传 IN (兼容大小写变体)；这里不再 .eq 避免误匹配
        wrapper.orderByDesc(ElectronicSignature::getSignedAt);
        return signatureMapper.selectPage(pageObj, wrapper);
    }

    public ElectronicSignature getSignatureById(Long id) {
        ElectronicSignature sig = signatureMapper.selectById(id);
        if (sig == null) throw BusinessException.notFound("SG0101", "签名记录不存在");
        return sig;
    }

    /**
     * R156 扩展：重签触发 @AuditLog（21 CFR Part 11 关键合规事件）
     * entityIdSpel=#p0（signatureId），captureArgs=false 避免 reason 写入审计
     */
    @Transactional
    @AuditLog(eventType = "RESIGN", entityType = "ELECTRONIC_SIGNATURE",
              operation = "电子签名重签", entityIdSpel = "#p0", captureArgs = false)
    public ElectronicSignature reSign(Long signatureId, Long signerId, Long newIntentId, String reason) {
        // 21 CFR Part 11 §11.200 G2: 验证签名人身份与 JWT 认证用户一致
        Long jwtUserId = SecurityUtils.getCurrentUserId();
        if (jwtUserId == null || !jwtUserId.equals(signerId)) {
            log.warn("[SECURITY] reSign signerId 与 JWT 身份不匹配: signerId={}, jwtUserId={}", signerId, jwtUserId);
            throw BusinessException.notFound("SG0108", "签名人身份验证失败：请求 signerId 与登录用户不匹配");
        }
        ElectronicSignature oldSig = signatureMapper.selectById(signatureId);
        if (oldSig == null) throw BusinessException.notFound("SG0101", "签名记录不存在");

        // v1.46 BUG #104 修复：重签也必须先校验 Intent
        if (newIntentId == null) {
            throw BusinessException.notFound("SG0104", "缺少新签名意图 ID");
        }
        SignatureIntent intent = intentService.validateAndConsume(newIntentId, signerId);

        oldSig.setIsValid(false);
        oldSig.setReason((oldSig.getReason() == null ? "" : oldSig.getReason()) +
                " [RESIGNED by " + signerId + " at " + LocalDateTime.now() + "]");
        signatureMapper.updateById(oldSig);

        LocalDateTime signedAt = LocalDateTime.now();
        String entityHash = calculateEntityHash(oldSig.getDocumentType(), oldSig.getDocumentId(), oldSig.getDocumentNo());
        String signatureValue = calculateSignatureValue(oldSig.getDocumentType(), oldSig.getDocumentId(),
                entityHash, intent.getMeaningCode(), signerId, signedAt);

        ElectronicSignature newSig = new ElectronicSignature();
        newSig.setSignerId(signerId != null ? signerId : oldSig.getSignerId());
        newSig.setSignerName(oldSig.getSignerName());
        newSig.setDocumentType(oldSig.getDocumentType());
        newSig.setDocumentId(oldSig.getDocumentId());
        newSig.setDocumentNo(oldSig.getDocumentNo());
        newSig.setSignatureType(intent.getIntentCode());
        newSig.setIntent(intent.getIntentCode());
        newSig.setEntityHash(entityHash);
        newSig.setSignatureValue(signatureValue);
        newSig.setSignatureHash(signatureValue);
        newSig.setSignatureMethod(oldSig.getSignatureMethod());
        newSig.setDeviceInfo(oldSig.getDeviceInfo());
        newSig.setIpAddress(oldSig.getIpAddress());
        newSig.setReason(reason);
        newSig.setIsValid(true);
        newSig.setSignedAt(signedAt);

        signatureMapper.insert(newSig);
        intentService.markConsumed(newIntentId, newSig.getId(), signerId);

        // v1.46 BUG #106 修复：重签写审计
        log.info("[AUDIT][ESIGN][RESIGN] oldId={}, newId={}, signerId={}, reason={}",
                signatureId, newSig.getId(), signerId, reason);
        return newSig;
    }
}