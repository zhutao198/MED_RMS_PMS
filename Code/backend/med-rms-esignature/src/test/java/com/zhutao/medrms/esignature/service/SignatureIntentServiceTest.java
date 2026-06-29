package com.zhutao.medrms.esignature.service;

import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.esignature.domain.entity.SignatureIntent;
import com.zhutao.medrms.esignature.mapper.SignatureIntentMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SignatureIntentService 单元测试（W11-D3）
 * 签名意图（21 CFR Part 11 §11.200 双签预备）
 */
@ExtendWith(MockitoExtension.class)
class SignatureIntentServiceTest {

    @Mock private SignatureIntentMapper intentMapper;

    @InjectMocks private SignatureIntentService service;

    @Test
    @DisplayName("createIntent-生成意图并插入")
    void createIntent() {
        when(intentMapper.insertIntent(any(SignatureIntent.class))).thenReturn(1);

        SignatureIntent intent = service.createIntent(100L, "REQUIREMENT", 1L, "REVIEW", "APPROVE");

        assertNotNull(intent);
        assertEquals(100L, intent.getRequesterId());
        assertEquals("REQUIREMENT", intent.getDocumentType());
        verify(intentMapper).insertIntent(any(SignatureIntent.class));
    }

    @Test
    @DisplayName("validateAndConsume-不存在抛错")
    void validateAndConsume_notFound() {
        when(intentMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class,
            () -> service.validateAndConsume(99L, 100L));
    }

    @Test
    @DisplayName("cancelIntent-存在则取消（需 requesterId 匹配）")
    void cancelIntent() {
        SignatureIntent intent = new SignatureIntent();
        intent.setId(1L);
        intent.setRequesterId(100L);
        intent.setStatus("PENDING");
        when(intentMapper.selectById(1L)).thenReturn(intent);

        service.cancelIntent(1L, 100L);

        verify(intentMapper).updateById(intent);
    }

    // ============================================================
    // W13-D3 边界用例扩充
    // ============================================================

    @Test
    @DisplayName("createIntent-meaningCode 缺省 = intentCode")
    void createIntent_meaningCodeDefault() {
        when(intentMapper.insertIntent(any(SignatureIntent.class))).thenReturn(1);

        SignatureIntent intent = service.createIntent(100L, "REQ", 1L, "REVIEW", null);

        assertEquals("REVIEW", intent.getMeaningCode(),
                "meaningCode 为 null 时应回退到 intentCode");
    }

    @Test
    @DisplayName("validateAndConsume-非 PENDING 状态抛 SG0105")
    void validateAndConsume_notPending() {
        SignatureIntent intent = new SignatureIntent();
        intent.setId(1L);
        intent.setStatus("CONSUMED");
        intent.setRequesterId(100L);
        when(intentMapper.selectById(1L)).thenReturn(intent);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateAndConsume(1L, 100L));
        assertEquals("SG0105", ex.getCode());
    }

    @Test
    @DisplayName("validateAndConsume-已过期抛 SG0106 + 自动标记 EXPIRED")
    void validateAndConsume_expired() {
        SignatureIntent intent = new SignatureIntent();
        intent.setId(1L);
        intent.setStatus("PENDING");
        intent.setRequesterId(100L);
        intent.setExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));
        when(intentMapper.selectById(1L)).thenReturn(intent);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateAndConsume(1L, 100L));
        assertEquals("SG0106", ex.getCode());
        verify(intentMapper).updateById(intent);
        assertEquals("EXPIRED", intent.getStatus());
    }

    @Test
    @DisplayName("validateAndConsume-申请人不一致抛 SG0107")
    void validateAndConsume_requesterMismatch() {
        SignatureIntent intent = new SignatureIntent();
        intent.setId(1L);
        intent.setStatus("PENDING");
        intent.setRequesterId(100L);
        intent.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(10));
        when(intentMapper.selectById(1L)).thenReturn(intent);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateAndConsume(1L, 999L));
        assertEquals("SG0107", ex.getCode());
    }

    @Test
    @DisplayName("validateAndConsume-PENDING + 未过期 + 匹配：返回 intent")
    void validateAndConsume_ok() {
        SignatureIntent intent = new SignatureIntent();
        intent.setId(1L);
        intent.setStatus("PENDING");
        intent.setRequesterId(100L);
        intent.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(10));
        when(intentMapper.selectById(1L)).thenReturn(intent);

        SignatureIntent result = service.validateAndConsume(1L, 100L);

        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("markConsumed-成功：状态 CONSUMED + 设置 consumedAt/By/signatureId")
    void markConsumed_ok() {
        SignatureIntent intent = new SignatureIntent();
        intent.setId(1L);
        intent.setStatus("PENDING");
        when(intentMapper.selectById(1L)).thenReturn(intent);

        service.markConsumed(1L, 200L, 100L);

        assertEquals("CONSUMED", intent.getStatus());
        assertEquals(100L, intent.getConsumedBy());
        assertEquals(200L, intent.getSignatureId());
        assertNotNull(intent.getConsumedAt());
        verify(intentMapper).updateById(intent);
    }

    @Test
    @DisplayName("markConsumed-intent 不存在静默返回")
    void markConsumed_notFound() {
        when(intentMapper.selectById(99L)).thenReturn(null);

        service.markConsumed(99L, 200L, 100L);

        verify(intentMapper, never()).updateById(any(SignatureIntent.class));
    }

    @Test
    @DisplayName("cancelIntent-非 PENDING 状态静默返回（不抛错）")
    void cancelIntent_notPending() {
        SignatureIntent intent = new SignatureIntent();
        intent.setId(1L);
        intent.setStatus("CONSUMED");
        when(intentMapper.selectById(1L)).thenReturn(intent);

        service.cancelIntent(1L, 100L);

        verify(intentMapper, never()).updateById(any(SignatureIntent.class));
    }

    @Test
    @DisplayName("cancelIntent-非申请人抛 SG0108")
    void cancelIntent_notRequester() {
        SignatureIntent intent = new SignatureIntent();
        intent.setId(1L);
        intent.setRequesterId(100L);
        intent.setStatus("PENDING");
        when(intentMapper.selectById(1L)).thenReturn(intent);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.cancelIntent(1L, 999L));
        assertEquals("SG0108", ex.getCode());
    }

    @Test
    @DisplayName("cancelIntent-intent 不存在静默返回")
    void cancelIntent_notFound() {
        when(intentMapper.selectById(99L)).thenReturn(null);

        service.cancelIntent(99L, 100L);

        verify(intentMapper, never()).updateById(any(SignatureIntent.class));
    }

    // ============================================================
    // R97 新增：listIntents 测试
    // ============================================================

    @Test
    @DisplayName("listIntents-正常调用 selectPage 并返回结果")
    void listIntents_ok() {
        // R97：MyBatis-Plus BaseMapper.selectPage 返回 IPage<T>，但 Mockito 重载推导需 Page 类型声明
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SignatureIntent> mockPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(0, 20);
        mockPage.setRecords(java.util.Collections.singletonList(new SignatureIntent()));
        when(intentMapper.selectPage(any(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class),
                any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                .thenReturn(mockPage);

        var result = service.listIntents(100L, "PENDING", 0, 20);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        verify(intentMapper).selectPage(any(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class),
                any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("listIntents-signerId=null 不抛错（避免 R90 同类 WHERE field=null bug）")
    void listIntents_signerIdNull() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SignatureIntent> mockPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(0, 20);
        when(intentMapper.selectPage(any(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class),
                any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                .thenReturn(mockPage);

        // 关键：signerId=null 时 service 内部用条件包裹式 eq(signerId != null, ...)，不应触发 WHERE field=null
        assertDoesNotThrow(() -> service.listIntents(null, null, 0, 20));
        verify(intentMapper).selectPage(any(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class),
                any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("listIntents-status=blank 不附加状态条件")
    void listIntents_statusBlank() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SignatureIntent> mockPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(0, 20);
        when(intentMapper.selectPage(any(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class),
                any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                .thenReturn(mockPage);

        // status 为 ""/null 时不附加条件
        assertDoesNotThrow(() -> service.listIntents(100L, "", 0, 20));
        assertDoesNotThrow(() -> service.listIntents(100L, null, 0, 20));
    }
}
