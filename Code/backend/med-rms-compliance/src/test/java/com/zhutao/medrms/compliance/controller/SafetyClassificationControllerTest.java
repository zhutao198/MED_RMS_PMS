package com.zhutao.medrms.compliance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.compliance.domain.entity.SafetyClassification;
import com.zhutao.medrms.compliance.service.SafetyClassificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SafetyClassificationController 单元测试（W14-D2）
 * IEC 62304 §5 软件安全分类 Controller
 */
@ExtendWith(MockitoExtension.class)
class SafetyClassificationControllerTest {

    @Mock private SafetyClassificationService service;

    @InjectMocks private SafetyClassificationController controller;

    private SafetyClassification newClass() {
        SafetyClassification c = new SafetyClassification();
        c.setId(1L);
        c.setSafetyClass("B");
        return c;
    }

    @Test
    @DisplayName("create-创建软件安全分类")
    void create() {
        when(service.create(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(newClass());

        SafetyClassificationController.CreateRequest req = new SafetyClassificationController.CreateRequest();
        req.setProjectId(100L);
        req.setSafetyClass("B");
        req.setRationale("可能导致严重伤害");
        req.setRemarks("IEC 62304 §5.3");

        Result<SafetyClassification> result = controller.create(req);

        assertEquals(200, result.getCode());
        assertEquals("B", result.getData().getSafetyClass());
    }

    @Test
    @DisplayName("review-复核（必须不同人）")
    void review() {
        when(service.review(anyLong(), anyLong())).thenReturn(newClass());

        Result<SafetyClassification> result = controller.review(1L, 200L);

        assertEquals(200, result.getCode());
        verify(service).review(1L, 200L);
    }

    @Test
    @DisplayName("get-按 ID 查询")
    void get() {
        when(service.getById(1L)).thenReturn(newClass());

        Result<SafetyClassification> result = controller.get(1L);

        assertEquals(1L, result.getData().getId());
    }

    @Test
    @DisplayName("list-分页查询")
    @SuppressWarnings("unchecked")
    void list() {
        IPage<SafetyClassification> mockPage = (IPage<SafetyClassification>) org.mockito.Mockito.mock(IPage.class);
        when(mockPage.getRecords()).thenReturn(List.of(newClass()));
        when(service.list(anyLong(), anyInt(), anyInt())).thenReturn(mockPage);

        Result<IPage<SafetyClassification>> result = controller.list(100L, 0, 20);

        assertEquals(1, result.getData().getRecords().size());
    }
}
