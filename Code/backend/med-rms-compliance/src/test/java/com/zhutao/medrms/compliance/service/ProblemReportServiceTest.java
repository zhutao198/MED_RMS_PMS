package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.compliance.domain.entity.ProblemReport;
import com.zhutao.medrms.compliance.mapper.ProblemReportMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ProblemReportService 单元测试（W2-D5）
 */
@ExtendWith(MockitoExtension.class)
class ProblemReportServiceTest {

    @Mock private ProblemReportMapper problemReportMapper;

    @InjectMocks private ProblemReportService service;

    // ============================================================
    // 1. create
    // ============================================================

    @Test
    @DisplayName("create-生成 PR-YYYYMMDD-XXXXXXXX 编号 + Open 状态")
    void create_ok() {
        ProblemReport pr = new ProblemReport();
        pr.setTitle("漏检");
        pr.setSeverity("HIGH");

        service.create(pr);

        ArgumentCaptor<ProblemReport> cap = ArgumentCaptor.forClass(ProblemReport.class);
        verify(problemReportMapper).insert(cap.capture());
        ProblemReport saved = cap.getValue();
        assertNotNull(saved.getReportCode());
        assertTrue(saved.getReportCode().startsWith("PR-"));
        assertEquals("Open", saved.getStatus());
    }

    // ============================================================
    // 2. 列表查询（4 种）
    // ============================================================

    @Test
    @DisplayName("listByProjectId-透传")
    void listByProjectId() {
        @SuppressWarnings("unchecked")
        IPage<ProblemReport> page = mock(IPage.class);
        when(problemReportMapper.selectByProjectId(any(), eq(1L))).thenReturn(page);

        assertSame(page, service.listByProjectId(1L, 1, 10));
    }

    @Test
    @DisplayName("listBySeverity-透传")
    void listBySeverity() {
        @SuppressWarnings("unchecked")
        IPage<ProblemReport> page = mock(IPage.class);
        when(problemReportMapper.selectBySeverity(any(), eq("HIGH"))).thenReturn(page);

        assertSame(page, service.listBySeverity("HIGH", 1, 10));
    }

    @Test
    @DisplayName("listByStatus-透传")
    void listByStatus() {
        @SuppressWarnings("unchecked")
        IPage<ProblemReport> page = mock(IPage.class);
        when(problemReportMapper.selectByStatus(any(), eq("Open"))).thenReturn(page);

        assertSame(page, service.listByStatus("Open", 1, 10));
    }

    @Test
    @DisplayName("listAll-透传")
    void listAll() {
        @SuppressWarnings("unchecked")
        IPage<ProblemReport> page = mock(IPage.class);
        when(problemReportMapper.selectAll(any())).thenReturn(page);

        assertSame(page, service.listAll(1, 10));
    }

    // ============================================================
    // 3. updateStatus
    // ============================================================

    @Test
    @DisplayName("updateStatus-不存在抛 CO0401")
    void updateStatus_notFound() {
        when(problemReportMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.updateStatus(99L, "Resolved", "fixed"));
        assertEquals("CO0401", ex.getCode());
    }

    @Test
    @DisplayName("updateStatus-Resolved → 写 resolvedAt")
    void updateStatus_resolved() {
        ProblemReport pr = new ProblemReport();
        pr.setId(1L);
        pr.setStatus("Open");
        when(problemReportMapper.selectById(1L)).thenReturn(pr);

        ProblemReport result = service.updateStatus(1L, "Resolved", "fixed by patch");

        assertEquals("Resolved", result.getStatus());
        assertEquals("fixed by patch", result.getResolution());
        assertNotNull(result.getResolvedAt());
    }

    @Test
    @DisplayName("updateStatus-非 Resolved/Closed 不写 resolvedAt")
    void updateStatus_inProgress() {
        ProblemReport pr = new ProblemReport();
        pr.setId(1L);
        pr.setStatus("Open");
        when(problemReportMapper.selectById(1L)).thenReturn(pr);

        ProblemReport result = service.updateStatus(1L, "Investigating", null);

        assertEquals("Investigating", result.getStatus());
        assertNull(result.getResolvedAt());
    }
}
