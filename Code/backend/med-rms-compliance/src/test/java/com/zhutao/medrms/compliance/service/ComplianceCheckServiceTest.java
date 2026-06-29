package com.zhutao.medrms.compliance.service;

import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.compliance.domain.entity.ComplianceCheck;
import com.zhutao.medrms.compliance.domain.entity.DhfEvidence;
import com.zhutao.medrms.compliance.mapper.ComplianceCheckMapper;
import com.zhutao.medrms.compliance.mapper.DhfEvidenceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ComplianceCheckService 单元测试（W12-D3）
 * 合规检查 + DHF 证据管理
 */
@ExtendWith(MockitoExtension.class)
class ComplianceCheckServiceTest {

    @Mock private ComplianceCheckMapper complianceCheckMapper;
    @Mock private DhfEvidenceMapper dhfEvidenceMapper;

    @InjectMocks private ComplianceCheckService service;

    private ComplianceCheck newCheck() {
        ComplianceCheck c = new ComplianceCheck();
        c.setId(1L);
        c.setRequirementId(100L);
        c.setCheckItem("IEC 62304 §5.1");
        return c;
    }

    private DhfEvidence newEvidence() {
        DhfEvidence e = new DhfEvidence();
        e.setId(1L);
        e.setProjectId(1L);
        e.setEvidenceType("URS");
        e.setIsDeleted(false);
        return e;
    }

    // ============================================================
    // 1. 合规检查
    // ============================================================

    @Test
    @DisplayName("listByRequirement-透传 mapper")
    void listByRequirement() {
        when(complianceCheckMapper.selectByRequirementId(100L))
                .thenReturn(List.of(newCheck()));
        assertEquals(1, service.listByRequirement(100L).size());
    }

    @Test
    @DisplayName("listByProject-透传 mapper")
    void listByProject() {
        when(complianceCheckMapper.selectByProjectId(1L))
                .thenReturn(List.of(newCheck(), newCheck()));
        assertEquals(2, service.listByProject(1L).size());
    }

    @Test
    @DisplayName("createCheck-默认状态 PENDING + 设置 checkedAt")
    void createCheck_ok() {
        ComplianceCheck input = new ComplianceCheck();
        input.setRequirementId(100L);
        input.setCheckItem("IEC 62304 §5.1");

        ComplianceCheck result = service.createCheck(input);

        assertEquals("PENDING", result.getStatus());
        assertNotNull(result.getCheckedAt());
        verify(complianceCheckMapper).insert(input);
    }

    @Test
    @DisplayName("completeCheck-成功：result + status=COMPLETED")
    void completeCheck_ok() {
        ComplianceCheck existing = newCheck();
        when(complianceCheckMapper.selectById(1L)).thenReturn(existing);

        ComplianceCheck result = service.completeCheck(1L, "PASS", "通过");

        assertEquals("PASS", result.getCheckResult());
        assertEquals("通过", result.getRemarks());
        assertEquals("COMPLETED", result.getStatus());
        assertNotNull(result.getCheckedAt());
        verify(complianceCheckMapper).updateById(existing);
    }

    @Test
    @DisplayName("completeCheck-记录不存在抛 CP0101")
    void completeCheck_notFound() {
        when(complianceCheckMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.completeCheck(99L, "PASS", ""));
        assertEquals("CP0101", ex.getCode());
    }

    // ============================================================
    // 2. DHF 证据
    // ============================================================

    @Test
    @DisplayName("listEvidenceByProject-透传 mapper")
    void listEvidenceByProject() {
        when(dhfEvidenceMapper.selectByProjectId(1L))
                .thenReturn(List.of(newEvidence()));
        assertEquals(1, service.listEvidenceByProject(1L).size());
    }

    @Test
    @DisplayName("listEvidenceByType-按类型过滤")
    void listEvidenceByType() {
        when(dhfEvidenceMapper.selectByType(1L, "URS"))
                .thenReturn(List.of(newEvidence()));
        assertEquals(1, service.listEvidenceByType(1L, "URS").size());
    }

    @Test
    @DisplayName("uploadEvidence-默认状态 UPLOADED + 设置 createdAt")
    void uploadEvidence_ok() {
        DhfEvidence input = new DhfEvidence();
        input.setProjectId(1L);
        input.setEvidenceType("SRS");

        DhfEvidence result = service.uploadEvidence(input);

        assertEquals("UPLOADED", result.getStatus());
        assertNotNull(result.getCreatedAt());
        verify(dhfEvidenceMapper).insert(input);
    }

    @Test
    @DisplayName("deleteEvidence-软删除：isDeleted=true")
    void deleteEvidence_ok() {
        DhfEvidence existing = newEvidence();
        when(dhfEvidenceMapper.selectById(1L)).thenReturn(existing);

        service.deleteEvidence(1L);

        assertTrue(existing.getIsDeleted());
        verify(dhfEvidenceMapper).updateById(existing);
    }

    @Test
    @DisplayName("deleteEvidence-不存在抛 CP0102")
    void deleteEvidence_notFound() {
        when(dhfEvidenceMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deleteEvidence(99L));
        assertEquals("CP0102", ex.getCode());
    }
}
