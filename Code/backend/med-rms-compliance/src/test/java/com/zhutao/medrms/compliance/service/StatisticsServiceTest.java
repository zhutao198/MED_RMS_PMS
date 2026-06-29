package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.change.domain.entity.ChangeRequest;
import com.zhutao.medrms.change.mapper.ChangeRequestMapper;
import com.zhutao.medrms.compliance.domain.entity.AuditLog;
import com.zhutao.medrms.compliance.mapper.AuditLogMapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.zhutao.medrms.risk.domain.entity.RiskRegister;
import com.zhutao.medrms.risk.mapper.RiskRegisterMapper;
import com.zhutao.medrms.risk.mapper.RiskAssessmentMapper;
import com.zhutao.medrms.compliance.mapper.StatisticsSnapshotMapper;
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
 * StatisticsService 单元测试（W10-D3）
 */
@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock private RequirementMapper requirementMapper;
    @Mock private ChangeRequestMapper changeRequestMapper;
    @Mock private RiskAssessmentMapper riskAssessmentMapper;
    @Mock private AuditLogMapper auditLogMapper;
    @Mock private StatisticsSnapshotMapper statisticsSnapshotMapper;

    @InjectMocks private StatisticsService service;

    @Test
    @DisplayName("getRequirementStats-返回 Map")
    void getRequirementStats() {
        lenient().when(requirementMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(10L);
        lenient().when(requirementMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> stats = service.getRequirementStats(1L);

        assertNotNull(stats);
        assertTrue(stats.containsKey("total"));
    }

    @Test
    @DisplayName("getChangeStats-返回 Map")
    void getChangeStats() {
        lenient().when(changeRequestMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> stats = service.getChangeStats(1L);

        assertNotNull(stats);
    }

    @Test
    @DisplayName("getRiskStats-返回 Map")
    void getRiskStats() {
        lenient().when(riskAssessmentMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> stats = service.getRiskStats(1L);

        assertNotNull(stats);
    }

    @Test
    @DisplayName("getComplianceStats-返回 Map")
    void getComplianceStats() {
        lenient().when(auditLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

        Map<String, Object> stats = service.getComplianceStats(1L);

        assertNotNull(stats);
    }
}
