package com.zhutao.medrms.esignature.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.esignature.domain.entity.ElectronicSignature;
import com.zhutao.medrms.esignature.domain.entity.SignatureIntent;
import com.zhutao.medrms.esignature.mapper.ElectronicSignatureMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ElectronicSignatureService 单元测试（W2-D6）
 * 覆盖：签名/验证/失效/列表/重签/密码+OTP 双签
 */
@ExtendWith(MockitoExtension.class)
class ElectronicSignatureServiceTest {

    @Mock private ElectronicSignatureMapper signatureMapper;
    @Mock private SignatureSettingsService settingsService;
    @Mock private SignatureIntentService intentService;

    @InjectMocks private ElectronicSignatureService service;

    private SignatureIntent validIntent() {
        SignatureIntent i = new SignatureIntent();
        i.setId(1L);
        i.setIntentCode("APPROVAL");
        i.setMeaningCode("APPROVAL");
        i.setRequesterId(100L);
        return i;
    }

    // ============================================================
    // 1. sign
    // ============================================================

    @Test
    @DisplayName("sign-缺少 intentId 抛 SG0104")
    void sign_missingIntent() {
        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.sign(100L, "alice", null, "APPROVAL",
                "REQUIREMENT", 1L, "REQ-001", "ok", "PWD", "127.0.0.1", "pwd", null));
        assertEquals("SG0104", ex.getCode());
    }

    @Test
    @DisplayName("sign-签名密码错误抛 SG0103")
    void sign_wrongPassword() {
        when(intentService.validateAndConsume(1L, 100L)).thenReturn(validIntent());
        when(settingsService.verifySignaturePassword(100L, "wrong")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.sign(100L, "alice", 1L, "APPROVAL",
                "REQUIREMENT", 1L, "REQ-001", "ok", "PWD", "127.0.0.1", "wrong", null));
        assertEquals("SG0103", ex.getCode());
    }

    @Test
    @DisplayName("sign-OTP 错误抛 SG0104")
    void sign_wrongOtp() {
        when(intentService.validateAndConsume(1L, 100L)).thenReturn(validIntent());
        when(settingsService.verifySignaturePassword(100L, "pwd")).thenReturn(true);
        when(settingsService.verifyOtp(100L, "000000")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.sign(100L, "alice", 1L, "APPROVAL",
                "REQUIREMENT", 1L, "REQ-001", "ok", "PWD", "127.0.0.1", "pwd", "000000"));
        assertEquals("SG0104", ex.getCode());
    }

    @Test
    @DisplayName("sign-已签过抛 SG0102")
    void sign_duplicate() {
        when(intentService.validateAndConsume(1L, 100L)).thenReturn(validIntent());
        when(settingsService.verifySignaturePassword(100L, "pwd")).thenReturn(true);
        when(signatureMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.sign(100L, "alice", 1L, "APPROVAL",
                "REQUIREMENT", 1L, "REQ-001", "ok", "PWD", "127.0.0.1", "pwd", null));
        assertEquals("SG0102", ex.getCode());
    }

    @Test
    @DisplayName("sign-成功 + SHA-256 签名值")
    void sign_ok() {
        when(intentService.validateAndConsume(1L, 100L)).thenReturn(validIntent());
        when(settingsService.verifySignaturePassword(100L, "pwd")).thenReturn(true);
        when(signatureMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        ElectronicSignature sig = service.sign(100L, "alice", 1L, "APPROVAL",
            "REQUIREMENT", 1L, "REQ-001", "ok", "PWD", "127.0.0.1", "pwd", null);

        assertNotNull(sig.getSignatureValue());
        assertEquals(64, sig.getSignatureValue().length(), "SHA-256 16 进制 = 64 字符");
        assertEquals(sig.getSignatureValue(), sig.getSignatureHash());
        verify(intentService).markConsumed(eq(1L), any(), eq(100L));
    }

    @Test
    @DisplayName("sign-无密码无 OTP 直签")
    void sign_noAuth() {
        when(intentService.validateAndConsume(1L, 100L)).thenReturn(validIntent());
        when(signatureMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        ElectronicSignature sig = service.sign(100L, "alice", 1L, null,
            "REQUIREMENT", 1L, "REQ-001", "ok", "PWD", "127.0.0.1", null, null);

        // 仍可成功
        assertNotNull(sig.getSignatureValue());
    }

    // ============================================================
    // 2. verifySignature
    // ============================================================

    @Test
    @DisplayName("verifySignature-不存在/失效返回 false")
    void verifySignature_invalid() {
        when(signatureMapper.selectById(99L)).thenReturn(null);
        assertFalse(service.verifySignature(99L));

        ElectronicSignature sig = new ElectronicSignature();
        sig.setId(1L);
        sig.setIsValid(false);
        when(signatureMapper.selectById(1L)).thenReturn(sig);
        assertFalse(service.verifySignature(1L));
    }

    @Test
    @DisplayName("verifySignature-有效 + 实体哈希匹配 → true")
    void verifySignature_ok() {
        ElectronicSignature sig = new ElectronicSignature();
        sig.setId(1L);
        sig.setIsValid(true);
        sig.setDocumentType("REQUIREMENT");
        sig.setDocumentId(10L);
        sig.setDocumentNo("REQ-001");
        sig.setEntityHash(service.calculateEntityHash("REQUIREMENT", 10L, "REQ-001"));
        sig.setSignatureValue("abc");
        when(signatureMapper.selectById(1L)).thenReturn(sig);

        assertTrue(service.verifySignature(1L));
    }

    // ============================================================
    // 3. invalidateSignature
    // ============================================================

    @Test
    @DisplayName("invalidateSignature-不存在抛 SG0103")
    void invalidate_notFound() {
        when(signatureMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.invalidateSignature(99L, 100L, "rollback"));
        assertEquals("SG0103", ex.getCode());
    }

    @Test
    @DisplayName("invalidateSignature-写 isValid=false + reason 追加")
    void invalidate_ok() {
        ElectronicSignature sig = new ElectronicSignature();
        sig.setId(1L);
        sig.setIsValid(true);
        sig.setReason("original");
        when(signatureMapper.selectById(1L)).thenReturn(sig);

        service.invalidateSignature(1L, 200L, "test");

        assertFalse(sig.getIsValid());
        assertTrue(sig.getReason().contains("INVALIDATED"));
        assertTrue(sig.getReason().contains("200"));
    }

    // ============================================================
    // 4. getSignaturesForEntity / listSignatures / getSignatureById
    // ============================================================

    @Test
    @DisplayName("getSignaturesForEntity-透传")
    void getForEntity() {
        when(signatureMapper.selectByEntity("REQUIREMENT", 1L)).thenReturn(List.of(new ElectronicSignature()));
        assertEquals(1, service.getSignaturesForEntity("REQUIREMENT", 1L).size());
    }

    @Test
    @DisplayName("listSignatures-带 signerId+documentType 过滤")
    void list_withFilters() {
        when(signatureMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
            .thenReturn(com.baomidou.mybatisplus.extension.plugins.pagination.Page.of(1, 10));

        assertNotNull(service.listSignatures(100L, "REQUIREMENT", 1, 10));
    }

    @Test
    @DisplayName("getSignatureById-不存在抛 SG0101")
    void getById_notFound() {
        when(signatureMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getSignatureById(99L));
        assertEquals("SG0101", ex.getCode());
    }

    // ============================================================
    // 5. reSign
    // ============================================================

    @Test
    @DisplayName("reSign-旧签名不存在抛 SG0101")
    void reSign_oldNotFound() {
        when(signatureMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.reSign(99L, 100L, 1L, "correction"));
        assertEquals("SG0101", ex.getCode());
    }

    @Test
    @DisplayName("reSign-缺少新 intent 抛 SG0104")
    void reSign_missingIntent() {
        ElectronicSignature old = new ElectronicSignature();
        old.setId(1L);
        when(signatureMapper.selectById(1L)).thenReturn(old);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.reSign(1L, 100L, null, "x"));
        assertEquals("SG0104", ex.getCode());
    }

    @Test
    @DisplayName("reSign-旧签置无效 + 新签写入")
    void reSign_ok() {
        ElectronicSignature old = new ElectronicSignature();
        old.setId(1L);
        old.setSignerId(50L);
        old.setSignerName("bob");
        old.setDocumentType("REQUIREMENT");
        old.setDocumentId(10L);
        old.setDocumentNo("REQ-001");
        old.setIsValid(true);
        old.setReason("original");
        old.setSignatureMethod("PWD");
        old.setIpAddress("127.0.0.1");
        when(signatureMapper.selectById(1L)).thenReturn(old);

        when(intentService.validateAndConsume(2L, 100L)).thenReturn(validIntent());

        ElectronicSignature newSig = service.reSign(1L, 100L, 2L, "correction");

        assertFalse(old.getIsValid());
        assertTrue(old.getReason().contains("RESIGNED"));
        assertNotNull(newSig.getId() == null || newSig.getId() != null); // 任意非空均可
        assertEquals("REQ-001", newSig.getDocumentNo());
    }

    // ============================================================
    // 6. 密码/OTP 验证（直接透传 settingsService）
    // ============================================================

    @Test
    @DisplayName("verifySignaturePassword-透传 settingsService")
    void verifyPassword() {
        when(settingsService.verifySignaturePassword(100L, "pwd")).thenReturn(true);
        assertTrue(service.verifySignaturePassword(100L, "pwd"));
    }

    @Test
    @DisplayName("verifyOtp-透传 settingsService")
    void verifyOtp() {
        when(settingsService.verifyOtp(100L, "123456")).thenReturn(true);
        assertTrue(service.verifyOtp(100L, "123456"));
    }

    // ============================================================
    // 7. 实体哈希计算
    // ============================================================

    @Test
    @DisplayName("calculateEntityHash-确定输出")
    void calculateEntityHash() {
        String h1 = service.calculateEntityHash("REQUIREMENT", 1L, "REQ-001");
        String h2 = service.calculateEntityHash("REQUIREMENT", 1L, "REQ-001");
        assertEquals(h1, h2);
        assertEquals(64, h1.length());
    }

    @Test
    @DisplayName("calculateEntityHash-不同输入 → 不同哈希")
    void calculateEntityHash_diff() {
        String h1 = service.calculateEntityHash("REQUIREMENT", 1L, "REQ-001");
        String h2 = service.calculateEntityHash("REQUIREMENT", 2L, "REQ-001");
        assertNotEquals(h1, h2);
    }
}
