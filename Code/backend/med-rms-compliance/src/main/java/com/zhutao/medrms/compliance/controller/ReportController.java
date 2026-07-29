package com.zhutao.medrms.compliance.controller;

import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.compliance.domain.entity.ReportConfig;
import com.zhutao.medrms.compliance.service.DhfEvidenceService;
import com.zhutao.medrms.compliance.service.DhfPdfRenderService;
import com.zhutao.medrms.compliance.service.ReportConfigService;
import com.zhutao.medrms.compliance.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Tag(name = "报表", description = "报表生成与下载接口")
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final DhfEvidenceService dhfEvidenceService;
    private final DhfPdfRenderService dhfPdfRenderService;
    private final ReportConfigService reportConfigService;

    @Operation(summary = "获取报表列表")
    @GetMapping
    public Result<List<Map<String, Object>>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String reportType) {
        return Result.success(reportService.getReports(projectId, reportType));
    }

    @Operation(summary = "生成报表")
    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestBody GenerateRequest request) {
        return Result.success(reportService.generateReport(request.getReportType(), request.getProjectId()));
    }

    @Operation(summary = "生成DHF合规证据包（JSON 结构，前端预览用）")
    @PostMapping("/dhf")
    public Result<Map<String, Object>> generateDhf(@RequestParam Long projectId) {
        return Result.success(dhfEvidenceService.generateDhfPackage(projectId));
    }

    // R225.2 CONTRACT-005：导出报表（前端 ReportExport.vue 调用）
    // 支持格式：pdf / excel / csv；缺省 csv
    @Operation(summary = "导出报表（按格式返回文件流）")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String reportType,
            @RequestParam(defaultValue = "csv") String format) {
        // R246.1 报表导出加固：
        // 1. reportType 长度限制（防超长字符串）
        // 2. format 白名单（仅允许 pdf/excel/csv）
        // 3. bytes 长度限制（防 OOM，默认 50MB）
        java.util.Set<String> ALLOWED_FORMATS = java.util.Set.of("pdf", "excel", "csv");
        if (!ALLOWED_FORMATS.contains(format.toLowerCase())) {
            throw BusinessException.param("非法的导出格式: " + format + "（允许: " + ALLOWED_FORMATS + "）");
        }
        String safeReportType = (reportType == null || reportType.isBlank()) ? "EXPORT" : reportType;
        if (safeReportType.length() > 50) {
            throw BusinessException.param("报表类型过长（限制 50 字符）");
        }
        // 生成报表数据（复用 reportService）
        Map<String, Object> reportData = reportService.generateReport(safeReportType, projectId);
        byte[] bytes;
        MediaType contentType;
        String fileExt;
        switch (format.toLowerCase()) {
            case "pdf":
                bytes = reportService.renderPdf(reportData);
                contentType = MediaType.APPLICATION_PDF;
                fileExt = ".pdf";
                break;
            case "excel":
                bytes = reportService.renderExcel(reportData);
                contentType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                fileExt = ".xlsx";
                break;
            case "csv":
            default:
                bytes = reportService.renderCsv(reportData);
                contentType = MediaType.parseMediaType("text/csv; charset=UTF-8");
                fileExt = ".csv";
                break;
        }
        // 防 OOM：超过 50MB 拒绝
        final int MAX_SIZE = 50 * 1024 * 1024;
        if (bytes.length > MAX_SIZE) {
            throw BusinessException.param("报表数据过大（" + bytes.length + " 字节 > " + MAX_SIZE + "），请缩小时间范围或筛选条件");
        }
        String fileName = "report_" + System.currentTimeMillis() + fileExt;
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        // RFC 5987 filename* UTF-8 编码（中文文件名跨浏览器兼容）
        headers.add("Content-Disposition", "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded);
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    /**
     * R207: 下载 DHF 证据包 PDF（FR-1.4 PRD §7.5.4）
     * 文件名规范：DHF-证据包-{projectNo}-{DCP阶段}-{yyyyMMdd}.pdf
     * Content-Type: application/pdf
     */
    @Operation(summary = "下载 DHF 证据包 PDF")
    @GetMapping("/dhf/download/{projectId}")
    public ResponseEntity<byte[]> downloadDhfPdf(@PathVariable Long projectId) {
        Map<String, Object> pkg = dhfEvidenceService.generateDhfPackage(projectId);
        byte[] pdf = dhfPdfRenderService.renderDhfPdf(pkg);
        String fileName = dhfPdfRenderService.buildFileName(pkg);
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", encoded);
        headers.setContentLength(pdf.length);
        headers.add("X-DHF-Status", String.valueOf(pkg.get("status")));
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @Operation(summary = "下载报表")
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long id) {
        byte[] data = reportService.downloadReport(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "report_" + id + ".txt");
        return ResponseEntity.ok().headers(headers).body(data);
    }

    // ========== v1.46 P1-后端-1：报表配置持久化 ==========

    @Operation(summary = "列出当前用户可见的报表配置（含共享）")
    @GetMapping("/configs")
    public Result<List<ReportConfig>> listConfigs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) Long projectId) {
        return Result.success(reportConfigService.listByCreator(userId, reportType, projectId));
    }

    @Operation(summary = "获取单个报表配置")
    @GetMapping("/configs/{id}")
    public Result<ReportConfig> getConfig(@PathVariable Long id) {
        return Result.success(reportConfigService.getById(id));
    }

    @Operation(summary = "创建报表配置（ReportsCustom.vue 持久化载体）")
    @PostMapping("/configs")
    public Result<ReportConfig> createConfig(@RequestBody ReportConfig config) {
        return Result.success(reportConfigService.create(config));
    }

    @Operation(summary = "更新报表配置")
    @PutMapping("/configs/{id}")
    public Result<ReportConfig> updateConfig(@PathVariable Long id, @RequestBody ReportConfig config) {
        return Result.success(reportConfigService.update(id, config));
    }

    @Operation(summary = "删除报表配置（软删）")
    @DeleteMapping("/configs/{id}")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        reportConfigService.delete(id);
        return Result.success(null);
    }

    @lombok.Data
    public static class GenerateRequest {
        private String reportType;
        private Long projectId;
    }
}
