package com.zhutao.medrms.requirement.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.requirement.domain.entity.RequirementPool;
import com.zhutao.medrms.requirement.mapper.RequirementPoolMapper;
import com.zhutao.medrms.requirement.service.RequirementPoolService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RequirementPoolController 单元测试（v1.27 R28）
 * 覆盖需求池列表/添加/转换
 */
@ExtendWith(MockitoExtension.class)
class RequirementPoolControllerTest {

    @Mock
    private RequirementPoolMapper poolMapper;

    @Mock
    private RequirementPoolService poolService;

    @InjectMocks
    private RequirementPoolController controller;

    @Test
    void list_returnsAll() {
        RequirementPool p = new RequirementPool();
        p.setId(1L);
        p.setSource("EMAIL");
        when(poolMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(p));

        Result<List<RequirementPool>> result = controller.list(null, null);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    void list_filteredByStatus() {
        when(poolMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        Result<List<RequirementPool>> result = controller.list("PENDING", null);

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void list_filteredBySource() {
        when(poolMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        Result<List<RequirementPool>> result = controller.list(null, "REGULATION");

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void add_returnsNewId() {
        RequirementPoolController.AddRequest req = new RequirementPoolController.AddRequest();
        req.setSource("EMAIL");
        req.setRawDescription("客户反馈");
        req.setCreatedBy(1L);
        when(poolService.addToPool(eq("EMAIL"), any(), eq("客户反馈"), eq(1L))).thenReturn(7L);

        Result<Long> result = controller.add(req);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(7L, result.getData());
        verify(poolService, times(1)).addToPool(eq("EMAIL"), any(), eq("客户反馈"), eq(1L));
    }

    @Test
    void convert_returnsNewRequirementId() {
        com.zhutao.medrms.requirement.domain.entity.Requirement urs = new com.zhutao.medrms.requirement.domain.entity.Requirement();
        urs.setId(100L);
        when(poolService.convertToUrs(eq(1L), eq(2L), eq("HIGH"))).thenReturn(urs);

        RequirementPoolController.ConvertRequest req = new RequirementPoolController.ConvertRequest();
        req.setProjectId(2L);
        req.setPriority("HIGH");

        Result<Long> result = controller.convert(1L, req);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(100L, result.getData());
    }
}
