package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.compliance.domain.entity.Report;
import com.zhutao.medrms.compliance.mapper.ReportMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReportService 单元测试（W12-D3）
 * 报表生成 + 下载（v1.41 BUG #42 不存在→404 修复 / #41 UTF-8 修复）
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ReportMapper reportMapper;

    @InjectMocks private ReportService service;

    private Report newReport(Long id, String type, Long projectId) {
        Report r = new Report();
        r.setId(id);
        r.setReportType(type);
        r.setProjectId(projectId);
        r.setTitle(type + " Report - Project " + projectId);
        r.setGeneratedAt(LocalDateTime.now());
        r.setDeleted(false);
        return r;
    }

    // ============================================================
    // 1. 查询
    // ============================================================

    @Test
    @DisplayName("getReports-按 projectId 过滤")
    void getReports_byProject() {
        when(reportMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(newReport(1L, "TRACEABILITY", 100L)));
        List<Map<String, Object>> result = service.getReports(100L, null);
        assertEquals(1, result.size());
        assertEquals("TRACEABILITY", result.get(0).get("reportType"));
    }

    @Test
    @DisplayName("getReports-按 reportType 过滤")
    void getReports_byType() {
        when(reportMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(newReport(1L, "RISK", 100L)));
        List<Map<String, Object>> result = service.getReports(null, "RISK");
        assertEquals("RISK", result.get(0).get("reportType"));
    }

    // ============================================================
    // 2. 生成（参数校验 + 白名单）
    // ============================================================

    @Test
    @DisplayName("generateReport-成功：插入 + generatedBy 兜底 1L")
    void generateReport_ok() {
        Map<String, Object> result = service.generateReport("TRACEABILITY", 100L);

        assertEquals("TRACEABILITY", result.get("reportType"));
        assertEquals(100L, result.get("projectId"));
        verify(reportMapper).insert(any(Report.class));
    }

    @Test
    @DisplayName("generateReport-空 reportType 抛 SY0101")
    void generateReport_blankType() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateReport("", 100L));
        assertEquals("SY0101", ex.getCode());
    }

    @Test
    @DisplayName("generateReport-非法 reportType 抛 SY0101")
    void generateReport_invalidType() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateReport("INVALID_TYPE", 100L));
        assertEquals("SY0101", ex.getCode());
    }

    @Test
    @DisplayName("generateReport-projectId 为 null 抛 SY0101")
    void generateReport_nullProject() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateReport("RISK", null));
        assertEquals("SY0101", ex.getCode());
    }

    // ============================================================
    // 3. 下载（v1.41 BUG #42 修复：不存在→404）
    // ============================================================

    @Test
    @DisplayName("downloadReport-成功：UTF-8 编码返回字节数组")
    void downloadReport_ok() {
        Report r = newReport(1L, "RISK", 100L);
        when(reportMapper.selectById(1L)).thenReturn(r);

        byte[] content = service.downloadReport(1L);

        assertNotNull(content);
        // 关键：必须用 UTF-8 解码，Windows 默认 GBK 会把 "Med-RMS 报表" 乱码
        String text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text.contains("Med-RMS 报表"));
        assertNotNull(r.getReportType());
    }

    @Test
    @DisplayName("downloadReport-不存在抛 RP0101（v1.41 BUG #42 修复）")
    void downloadReport_notFound() {
        when(reportMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.downloadReport(99L));
        assertEquals("RP0101", ex.getCode());
    }
}
