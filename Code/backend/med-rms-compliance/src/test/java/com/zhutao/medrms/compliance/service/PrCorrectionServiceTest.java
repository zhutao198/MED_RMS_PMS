package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.compliance.domain.entity.PrCorrection;
import com.zhutao.medrms.compliance.mapper.PrCorrectionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PrCorrectionService 单元测试（W12-D3）
 * v1.47 P0 BUG #124 双人签名锁定（验证人 ≠ 责任人）
 */
@ExtendWith(MockitoExtension.class)
class PrCorrectionServiceTest {

    @Mock private PrCorrectionMapper mapper;

    @InjectMocks private PrCorrectionService service;

    private PrCorrection newCorrection(Long id, Long ownerId) {
        PrCorrection c = new PrCorrection();
        c.setId(id);
        c.setProblemReportId(100L);
        c.setAction("更新 SRS");
        c.setOwnerId(ownerId);
        c.setStatus(PrCorrection.STATUS_OPEN);
        return c;
    }

    // ============================================================
    // 1. 创建
    // ============================================================

    @Test
    @DisplayName("create-状态默认 OPEN + 设置 createdAt/updatedAt")
    void create_ok() {
        PrCorrection result = service.create(100L, "更新 SRS", 1L,
                LocalDateTime.now().plusDays(7));

        assertEquals(100L, result.getProblemReportId());
        assertEquals("更新 SRS", result.getAction());
        assertEquals(1L, result.getOwnerId());
        assertEquals(PrCorrection.STATUS_OPEN, result.getStatus());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
        verify(mapper).insert(result);
    }

    // ============================================================
    // 2. 完成（责任人自填效果）
    // ============================================================

    @Test
    @DisplayName("complete-状态 IN_PROGRESS + 设置 completedAt + 效果")
    void complete_ok() {
        PrCorrection existing = newCorrection(1L, 10L);
        when(mapper.selectById(1L)).thenReturn(existing);

        PrCorrection result = service.complete(1L, "有效");

        assertEquals(PrCorrection.STATUS_IN_PROGRESS, result.getStatus());
        assertEquals("有效", result.getEffectiveness());
        assertNotNull(result.getCompletedAt());
        verify(mapper).updateById(existing);
    }

    @Test
    @DisplayName("complete-记录不存在抛 CO0201")
    void complete_notFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.complete(99L, "有效"));
        assertEquals("CO0201", ex.getCode());
    }

    // ============================================================
    // 3. 验证（v1.47 P0 BUG #124：验证人 ≠ 责任人）
    // ============================================================

    @Test
    @DisplayName("verify-验证人≠责任人：状态 VERIFIED + verifiedBy/verifiedAt")
    void verify_ok() {
        PrCorrection existing = newCorrection(1L, 10L);
        when(mapper.selectById(1L)).thenReturn(existing);

        PrCorrection result = service.verify(1L, 20L);

        assertEquals(20L, result.getVerifiedBy());
        assertNotNull(result.getVerifiedAt());
        assertEquals(PrCorrection.STATUS_VERIFIED, result.getStatus());
        verify(mapper).updateById(existing);
    }

    @Test
    @DisplayName("verify-验证人==责任人抛 CO0202（v1.47 BUG #124 修复）")
    void verify_sameAsOwner() {
        PrCorrection existing = newCorrection(1L, 10L);
        when(mapper.selectById(1L)).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verify(1L, 10L));
        assertEquals("CO0202", ex.getCode());
        verify(mapper, never()).updateById(any(PrCorrection.class));
    }

    @Test
    @DisplayName("verify-记录不存在抛 CO0201")
    void verify_notFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verify(99L, 20L));
        assertEquals("CO0201", ex.getCode());
    }

    // ============================================================
    // 4. 查询
    // ============================================================

    @Test
    @DisplayName("getById-存在则返回")
    void getById_exists() {
        PrCorrection existing = newCorrection(1L, 10L);
        when(mapper.selectById(1L)).thenReturn(existing);
        assertEquals(1L, service.getById(1L).getId());
    }

    @Test
    @DisplayName("getById-不存在抛 CO0201")
    void getById_notFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getById(99L));
        assertEquals("CO0201", ex.getCode());
    }

    // ============================================================
    // 5. 分页
    // ============================================================

    @Test
    @DisplayName("listByReport-透传 mapper 分页")
    @SuppressWarnings("unchecked")
    void listByReport() {
        IPage<PrCorrection> mockPage = (IPage<PrCorrection>) org.mockito.Mockito.mock(IPage.class);
        when(mockPage.getRecords()).thenReturn(List.of(newCorrection(1L, 10L)));
        when(mapper.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        IPage<PrCorrection> result = service.listByReport(100L, 1, 20);

        assertEquals(1, result.getRecords().size());
    }
}
