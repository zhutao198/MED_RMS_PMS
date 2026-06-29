package com.zhutao.medrms.compliance.service;

import com.zhutao.medrms.compliance.domain.entity.SafetyClassification;
import com.zhutao.medrms.compliance.mapper.SafetyClassificationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SafetyClassificationService 单元测试（W10-D3）
 * 安全分类：A / B / C（IEC 62304 软件安全等级）
 */
@ExtendWith(MockitoExtension.class)
class SafetyClassificationServiceTest {

    @Mock private SafetyClassificationMapper scMapper;

    @InjectMocks private SafetyClassificationService service;

    @Test
    @DisplayName("create-插入安全分类")
    void create() {
        when(scMapper.insert(any(SafetyClassification.class))).thenReturn(1);

        SafetyClassification sc = service.create(1L, "C", "rationale", "remarks");

        assertEquals(1L, sc.getProjectId());
        assertEquals("C", sc.getSafetyClass());
        verify(scMapper).insert(any(SafetyClassification.class));
    }

    @Test
    @DisplayName("review-更新 reviewedBy + reviewedAt")
    void review() {
        SafetyClassification sc = new SafetyClassification();
        sc.setId(1L);
        sc.setSafetyClass("C");
        sc.setStatus("DRAFT");
        when(scMapper.selectById(1L)).thenReturn(sc);

        SafetyClassification result = service.review(1L, 100L);

        assertEquals(100L, result.getReviewedBy());
        assertNotNull(result.getReviewedAt());
        verify(scMapper).updateById(sc);
    }

    @Test
    @DisplayName("getById-透传")
    void getById() {
        SafetyClassification sc = new SafetyClassification();
        sc.setId(1L);
        when(scMapper.selectById(1L)).thenReturn(sc);
        assertSame(sc, service.getById(1L));
    }
}

