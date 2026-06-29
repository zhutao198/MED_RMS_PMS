package com.zhutao.medrms.requirement.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.service.RequirementService;
import com.zhutao.medrms.requirement.service.RequirementVersionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RequirementController 单元测试（v1.27 R28）
 * 覆盖需求列表/详情/创建/更新/拆解/审批
 */
@ExtendWith(MockitoExtension.class)
class RequirementControllerTest {

    @Mock
    private RequirementService requirementService;

    @Mock
    private RequirementVersionService versionService;

    @InjectMocks
    private RequirementController controller;

    @Test
    void listRequirements_returnsPage() {
        Requirement r = new Requirement(); r.setId(1L); r.setTitle("URS-001");
        Page<Requirement> page = new Page<>(0, 20);
        page.setRecords(Collections.singletonList(r));
        page.setTotal(1);
        when(requirementService.listRequirements(any(), any(), any(), any(), any(), eq(0), eq(20))).thenReturn(page);

        Result<IPage<Requirement>> result = controller.listRequirements(1L, null, null, null, null, 0, 20);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().getRecords().size());
    }

    @Test
    void getRequirement_returnsDetail() {
        Requirement r = new Requirement(); r.setId(1L); r.setTitle("URS-001");
        when(requirementService.getRequirementById(1L)).thenReturn(r);

        Result<Requirement> result = controller.getRequirement(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(1L, result.getData().getId());
    }

    @Test
    void createRequirement_returnsNewReq() {
        Requirement input = new Requirement(); input.setTitle("NEW");
        Requirement saved = new Requirement(); saved.setId(99L); saved.setTitle("NEW");
        when(requirementService.createRequirement(any(), any())).thenReturn(saved);

        Result<Requirement> result = controller.createRequirement(input);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(99L, result.getData().getId());
    }

    @Test
    void updateRequirement_returnsUpdated() {
        Requirement updates = new Requirement(); updates.setTitle("UPD");
        when(requirementService.updateRequirement(eq(1L), any())).thenReturn(updates);

        Result<Requirement> result = controller.updateRequirement(1L, updates);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("UPD", result.getData().getTitle());
    }

    @Test
    void approveRequirement_returnsSuccess() {
        Result<Void> result = controller.approveRequirement(1L, "APPROVED", 2L, "ok");

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(requirementService, times(1)).approveRequirement(1L, "APPROVED", 2L, "ok");
    }

    @Test
    void baselineRequirements_returnsSuccess() {
        Result<Void> result = controller.baselineRequirements(1L, Arrays.asList(10L, 20L));

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(requirementService, times(1)).baselineRequirements(eq(1L), any());
    }

    @Test
    void getVersions_returnsHistory() {
        when(versionService.getVersionsByRequirementId(1L)).thenReturn(Collections.emptyList());

        Result<List<com.zhutao.medrms.requirement.domain.entity.RequirementVersion>> result =
            controller.getRequirementVersions(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }
}
