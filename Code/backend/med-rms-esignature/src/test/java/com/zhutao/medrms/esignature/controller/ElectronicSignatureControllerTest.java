package com.zhutao.medrms.esignature.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhutao.medrms.esignature.domain.entity.ElectronicSignature;
import com.zhutao.medrms.esignature.domain.entity.SignatureIntent;
import com.zhutao.medrms.esignature.service.ElectronicSignatureService;
import com.zhutao.medrms.esignature.service.SignatureIntentService;
import com.zhutao.medrms.esignature.service.SignatureSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ElectronicSignatureControllerTest {

    @Mock
    private ElectronicSignatureService signatureService;

    @Mock
    private SignatureSettingsService settingsService;

    @Mock
    private SignatureIntentService intentService;

    @InjectMocks
    private ElectronicSignatureController controller;

    @Test
    void testSign() {
        ElectronicSignature signature = new ElectronicSignature();
        signature.setId(1L);
        signature.setIntent("APPROVE");
        signature.setSignerId(1L);
        signature.setSignerName("测试用户");
        signature.setIsValid(true);

        ElectronicSignatureController.SignRequest request = new ElectronicSignatureController.SignRequest();
        request.setSignerId(1L);
        request.setSignerName("测试用户");
        // v1.47 BUG #104：sign() 必须有 intentId + meaningCode
        request.setIntentId(1L);
        request.setMeaningCode("APPROVE");
        request.setDocumentType("Requirement");
        request.setDocumentId(100L);
        request.setReason("测试原因");

        when(signatureService.sign(any(Long.class), any(), any(Long.class), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(signature);

        var result = controller.sign(request);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("APPROVE", result.getData().getIntent());
    }

    @Test
    void testVerifySignature() {
        // R96 修复：verifySignature 端点返回 Map<String, Object>（含 valid/signerName/signTime/message）
        // 原测试期望 true（boolean）已过时，与 R96 controller 改动不匹配 — R97 联调时修正
        java.util.Map<String, Object> mockResult = new java.util.HashMap<>();
        mockResult.put("valid", true);
        mockResult.put("signerName", "测试用户");
        mockResult.put("signTime", "2026-06-17T19:00:00");
        mockResult.put("message", "签名有效");
        when(signatureService.verifySignatureWithDetail(1L)).thenReturn(mockResult);

        var result = controller.verifySignature(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals(true, result.getData().get("valid"));
    }

    @Test
    void testGetSignaturesForEntity() {
        ElectronicSignature signature = new ElectronicSignature();
        signature.setId(1L);
        signature.setDocumentType("Requirement");
        signature.setDocumentId(100L);

        when(signatureService.getSignaturesForEntity("Requirement", 100L)).thenReturn(List.of(signature));

        var result = controller.getSignaturesForEntity("Requirement", 100L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    void testListSignatures() {
        ElectronicSignature signature = new ElectronicSignature();
        signature.setId(1L);
        Page<ElectronicSignature> page = new Page<>(0, 20);
        page.setRecords(List.of(signature));
        page.setTotal(1);

        when(signatureService.listSignatures(any(), any(), anyInt(), anyInt())).thenReturn(page);

        var result = controller.listSignatures(null, null, 0, 20);

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    // ============================================================
    // R97 新增：listIntents 端点测试
    // ============================================================

    @Test
    void testListIntents() {
        SignatureIntent intent = new SignatureIntent();
        intent.setId(1L);
        intent.setStatus("PENDING");
        Page<SignatureIntent> page = new Page<>(0, 20);
        page.setRecords(List.of(intent));
        page.setTotal(1);

        when(intentService.listIntents(any(), any(), anyInt(), anyInt())).thenReturn(page);

        var result = controller.listIntents(100L, "PENDING", 0, 20);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().getRecords().size());
    }

    @Test
    void testListIntents_nullParams() {
        Page<SignatureIntent> page = new Page<>(0, 20);
        when(intentService.listIntents(any(), any(), anyInt(), anyInt())).thenReturn(page);

        // signerId=null + status=null：不应抛错
        var result = controller.listIntents(null, null, 0, 20);

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void testGetSignature() {
        ElectronicSignature signature = new ElectronicSignature();
        signature.setId(1L);
        signature.setSignerName("测试用户");

        when(signatureService.getSignatureById(1L)).thenReturn(signature);

        var result = controller.getSignature(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("测试用户", result.getData().getSignerName());
    }

    @Test
    void testReSign() {
        ElectronicSignature newSignature = new ElectronicSignature();
        newSignature.setId(2L);
        newSignature.setIsValid(true);

        ElectronicSignatureController.ReSignRequest request = new ElectronicSignatureController.ReSignRequest();
        request.setSignerId(1L);
        // v1.47 BUG #104：reSign 必须有 newIntentId
        request.setNewIntentId(2L);
        request.setReason("重新签名原因");

        when(signatureService.reSign(eq(1L), any(), any(), any())).thenReturn(newSignature);

        var result = controller.reSign(1L, request);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(2L, result.getData().getId());
    }
}
