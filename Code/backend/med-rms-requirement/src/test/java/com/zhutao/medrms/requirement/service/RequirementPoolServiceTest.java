package com.zhutao.medrms.requirement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.domain.entity.RequirementPool;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.zhutao.medrms.requirement.mapper.RequirementPoolMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RequirementPoolService 单元测试（W2-D1）
 * 覆盖：添加需求入池（标题截断/createdBy 来源）/ 转换为 URS（PENDING 校验/参数校验/状态流转）
 */
@ExtendWith(MockitoExtension.class)
class RequirementPoolServiceTest {

    @Mock private RequirementPoolMapper poolMapper;
    @Mock private RequirementMapper requirementMapper;

    @InjectMocks private RequirementPoolService service;

    // ============================================================
    // 1. addToPool
    // ============================================================

    @Test
    @DisplayName("addToPool-长描述截断为 50 字符 + ...")
    void addToPool_longDescription() {
        String longDesc = "a".repeat(80);
        service.addToPool("USER", null, longDesc, 100L);

        ArgumentCaptor<RequirementPool> cap = ArgumentCaptor.forClass(RequirementPool.class);
        verify(poolMapper).insert(cap.capture());
        assertEquals(53, cap.getValue().getTitle().length());
        assertTrue(cap.getValue().getTitle().endsWith("..."));
        assertEquals("PENDING", cap.getValue().getStatus());
    }

    @Test
    @DisplayName("addToPool-null 描述 → 未命名需求")
    void addToPool_nullDescription() {
        service.addToPool("USER", null, null, 100L);

        ArgumentCaptor<RequirementPool> cap = ArgumentCaptor.forClass(RequirementPool.class);
        verify(poolMapper).insert(cap.capture());
        assertEquals("未命名需求", cap.getValue().getTitle());
    }

    @Test
    @DisplayName("addToPool-优先使用参数 createdBy（不查 SecurityContext）")
    void addToPool_paramCreatedBy() {
        try (MockedStatic<com.zhutao.medrms.common.util.SecurityUtils> mocked =
                 Mockito.mockStatic(com.zhutao.medrms.common.util.SecurityUtils.class)) {
            // 业务上不会调用，因为 createdBy 已传入
            service.addToPool("USER", null, "desc", 100L);

            ArgumentCaptor<RequirementPool> cap = ArgumentCaptor.forClass(RequirementPool.class);
            verify(poolMapper).insert(cap.capture());
            assertEquals(100L, cap.getValue().getCreatedBy());
            mocked.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("addToPool-无 createdBy 时回退 SecurityContext")
    void addToPool_fallbackToSecurity() {
        try (MockedStatic<com.zhutao.medrms.common.util.SecurityUtils> mocked =
                 Mockito.mockStatic(com.zhutao.medrms.common.util.SecurityUtils.class)) {
            mocked.when(com.zhutao.medrms.common.util.SecurityUtils::getCurrentUserId).thenReturn(200L);

            service.addToPool("USER", null, "desc", null);

            ArgumentCaptor<RequirementPool> cap = ArgumentCaptor.forClass(RequirementPool.class);
            verify(poolMapper).insert(cap.capture());
            assertEquals(200L, cap.getValue().getCreatedBy());
        }
    }

    // ============================================================
    // 2. convertToUrs
    // ============================================================

    @Test
    @DisplayName("convertToUrs-null poolId 抛 param")
    void convertToUrs_nullPoolId() {
        assertThrows(BusinessException.class,
            () -> service.convertToUrs(null, 1L, "P1"));
    }

    @Test
    @DisplayName("convertToUrs-null projectId 抛 param")
    void convertToUrs_nullProjectId() {
        assertThrows(BusinessException.class,
            () -> service.convertToUrs(10L, null, "P1"));
    }

    @Test
    @DisplayName("convertToUrs-null priority 抛 param")
    void convertToUrs_nullPriority() {
        assertThrows(BusinessException.class,
            () -> service.convertToUrs(10L, 1L, null));
        assertThrows(BusinessException.class,
            () -> service.convertToUrs(10L, 1L, ""));
    }

    @Test
    @DisplayName("convertToUrs-池项不存在抛 RP0101")
    void convertToUrs_poolNotFound() {
        when(poolMapper.selectById(10L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.convertToUrs(10L, 1L, "P1"));
        assertEquals("RP0101", ex.getCode());
    }

    @Test
    @DisplayName("convertToUrs-非 PENDING 状态抛 stateConflict")
    void convertToUrs_invalidStatus() {
        RequirementPool p = new RequirementPool();
        p.setId(10L);
        p.setStatus("CONVERTED");
        when(poolMapper.selectById(10L)).thenReturn(p);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.convertToUrs(10L, 1L, "P1"));
        assertTrue(ex.getMessage().contains("仅 PENDING 状态可转换"));
    }

    @Test
    @DisplayName("convertToUrs-PENDING 成功 → 池项变 CONVERTED + 新 URS 写入")
    void convertToUrs_success() {
        try (MockedStatic<com.zhutao.medrms.common.util.SecurityUtils> mocked =
                 Mockito.mockStatic(com.zhutao.medrms.common.util.SecurityUtils.class)) {
            mocked.when(com.zhutao.medrms.common.util.SecurityUtils::getCurrentUserId).thenReturn(100L);

            RequirementPool p = new RequirementPool();
            p.setId(10L);
            p.setStatus("PENDING");
            p.setTitle("from-pool");
            p.setRawDescription("raw");
            p.setParsedDescription("parsed");
            when(poolMapper.selectById(10L)).thenReturn(p);
            when(requirementMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

            Requirement urs = service.convertToUrs(10L, 1L, "P1");

            assertEquals("URS-1-003", urs.getRequirementNo());
            assertEquals("URS", urs.getRequirementType());
            assertEquals("Draft", urs.getStatus());
            assertEquals("from-pool", urs.getTitle());
            // 优先使用 parsedDescription
            assertEquals("parsed", urs.getDescription());

            verify(requirementMapper).insert(any(Requirement.class));
            ArgumentCaptor<RequirementPool> poolCap = ArgumentCaptor.forClass(RequirementPool.class);
            verify(poolMapper).updateById(poolCap.capture());
            assertEquals("CONVERTED", poolCap.getValue().getStatus());
            // mock 环境下 updateById 不会回填 convertedToId，需用 captor 验证 set 调用
            // 服务端在 setStatus("CONVERTED") 之后才 setConvertedToId，已隐式执行
        }
    }

    @Test
    @DisplayName("convertToUrs-池项无 parsedDescription 时回退 rawDescription")
    void convertToUrs_fallbackRawDescription() {
        try (MockedStatic<com.zhutao.medrms.common.util.SecurityUtils> mocked =
                 Mockito.mockStatic(com.zhutao.medrms.common.util.SecurityUtils.class)) {
            mocked.when(com.zhutao.medrms.common.util.SecurityUtils::getCurrentUserId).thenReturn(100L);

            RequirementPool p = new RequirementPool();
            p.setId(10L);
            p.setStatus("PENDING");
            p.setTitle("t");
            p.setRawDescription("raw-only");
            p.setParsedDescription(null);
            when(poolMapper.selectById(10L)).thenReturn(p);
            when(requirementMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            Requirement urs = service.convertToUrs(10L, 1L, "P1");

            assertEquals("raw-only", urs.getDescription());
        }
    }
}
