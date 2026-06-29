package com.zhutao.medrms.compliance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.compliance.domain.entity.PrCorrection;
import com.zhutao.medrms.compliance.service.PrCorrectionService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PrCorrectionController 单元测试（W14-D1）
 * ISO 13485 §8.5.2 CAPA 子表 Controller
 */
@ExtendWith(MockitoExtension.class)
class PrCorrectionControllerTest {

    @Mock private PrCorrectionService service;

    @InjectMocks private PrCorrectionController controller;

    private PrCorrection newCorrection(Long id) {
        PrCorrection c = new PrCorrection();
        c.setId(id);
        c.setStatus(PrCorrection.STATUS_OPEN);
        return c;
    }

    @Test
    @DisplayName("create-创建纠正措施")
    void create() {
        when(service.create(anyLong(), anyString(), anyLong(), any())).thenReturn(newCorrection(1L));

        PrCorrectionController.CreateRequest req = new PrCorrectionController.CreateRequest();
        req.setProblemReportId(100L);
        req.setAction("更新 SRS");
        req.setOwnerId(10L);
        req.setDueDate(LocalDateTime.now().plusDays(7));

        Result<PrCorrection> result = controller.create(req);

        assertEquals(200, result.getCode());
        assertEquals(1L, result.getData().getId());
    }

    @Test
    @DisplayName("complete-完成纠正措施")
    void complete() {
        when(service.complete(anyLong(), anyString())).thenReturn(newCorrection(1L));

        Result<PrCorrection> result = controller.complete(1L, "有效");

        assertEquals(200, result.getCode());
        verify(service).complete(1L, "有效");
    }

    @Test
    @DisplayName("verify-验证纠正措施（必须不同人）")
    void verifyCorrection() {
        when(service.verify(anyLong(), anyLong())).thenReturn(newCorrection(1L));

        Result<PrCorrection> result = controller.verify(1L, 20L);

        assertEquals(200, result.getCode());
        verify(service).verify(1L, 20L);
    }

    @Test
    @DisplayName("get-按 ID 查询")
    void get() {
        when(service.getById(1L)).thenReturn(newCorrection(1L));

        Result<PrCorrection> result = controller.get(1L);

        assertEquals(1L, result.getData().getId());
    }

    @Test
    @DisplayName("listByReport-分页查询")
    @SuppressWarnings("unchecked")
    void listByReport() {
        IPage<PrCorrection> mockPage = (IPage<PrCorrection>) org.mockito.Mockito.mock(IPage.class);
        when(mockPage.getRecords()).thenReturn(List.of(newCorrection(1L)));
        when(service.listByReport(100L, 0, 20)).thenReturn(mockPage);

        Result<IPage<PrCorrection>> result = controller.listByReport(100L, 0, 20);

        assertEquals(1, result.getData().getRecords().size());
        verify(service).listByReport(100L, 0, 20);
    }

    @Test
    @DisplayName("listByReport-自定义分页参数")
    void listByReport_customPaging() {
        when(service.listByReport(anyLong(), anyInt(), anyInt())).thenReturn(
                org.mockito.Mockito.mock(IPage.class));

        controller.listByReport(100L, 2, 50);

        verify(service).listByReport(100L, 2, 50);
    }
}
