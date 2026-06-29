package com.zhutao.medrms.requirement.service;

import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.zhutao.medrms.requirement.mapper.RequirementVersionMapper;
import com.zhutao.medrms.requirement.mapper.TestCaseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * QualityScoreService 单元测试（W11-D3）
 * 需求质量分（基于追溯完整度）
 */
@ExtendWith(MockitoExtension.class)
class QualityScoreServiceTest {

    @Mock private RequirementMapper requirementMapper;
    @Mock private TestCaseMapper testCaseMapper;
    @Mock private RequirementVersionMapper versionMapper;

    @InjectMocks private QualityScoreService service;

    @Test
    @DisplayName("score-返回质量分 Map")
    void score() {
        lenient().when(requirementMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = service.score(1L);

        assertNotNull(result);
    }

    @Test
    @DisplayName("scoreAll-返回项目下所有需求质量分")
    void scoreAll() {
        lenient().when(requirementMapper.selectList(any())).thenReturn(List.of());

        List<Map<String, Object>> result = service.scoreAll(1L);

        assertNotNull(result);
    }
}
