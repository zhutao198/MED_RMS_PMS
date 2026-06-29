package com.zhutao.medrms.compliance.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.compliance.service.DhfEvidenceService;
import com.zhutao.medrms.compliance.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ReportController 单元测试（v1.27 R28）
 * 覆盖报表列表/生成/DHF证据包/下载
 */
@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private ReportService reportService;

    @Mock
    private DhfEvidenceService dhfEvidenceService;

    @InjectMocks
    private ReportController controller;

    @Test
    void list_returnsReports() {
        Map<String, Object> r = new HashMap<>();
        r.put("id", 1L);
        r.put("type", "summary");
        when(reportService.getReports(any(), any())).thenReturn(List.of(r));

        Result<List<Map<String, Object>>> result = controller.list(null, null);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    void generate_returnsReportData() {
        Map<String, Object> data = new HashMap<>();
        data.put("reportId", 100L);
        data.put("status", "GENERATED");
        when(reportService.generateReport(eq("summary"), eq(1L))).thenReturn(data);

        ReportController.GenerateRequest req = new ReportController.GenerateRequest();
        req.setReportType("summary");
        req.setProjectId(1L);

        Result<Map<String, Object>> result = controller.generate(req);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("GENERATED", result.getData().get("status"));
    }

    @Test
    void generateDhf_returnsPackage() {
        Map<String, Object> pkg = new HashMap<>();
        pkg.put("packageId", "DHF-001");
        pkg.put("items", 25);
        when(dhfEvidenceService.generateDhfPackage(1L)).thenReturn(pkg);

        Result<Map<String, Object>> result = controller.generateDhf(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(25, result.getData().get("items"));
    }

    @Test
    void downloadReport_returnsBytes() {
        byte[] data = "REPORT-CONTENT".getBytes();
        when(reportService.downloadReport(1L)).thenReturn(data);

        ResponseEntity<byte[]> result = controller.downloadReport(1L);

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertArrayEquals(data, result.getBody());
    }
}
