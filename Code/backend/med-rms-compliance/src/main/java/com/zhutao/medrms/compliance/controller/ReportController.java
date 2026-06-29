package com.zhutao.medrms.compliance.controller;

import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.compliance.domain.entity.ReportConfig;
import com.zhutao.medrms.compliance.service.DhfEvidenceService;
import com.zhutao.medrms.compliance.service.ReportConfigService;
import com.zhutao.medrms.compliance.service.ReportService;
import com.zhutao.medrms.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "报表", description = "报表生成与下载接口")
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final DhfEvidenceService dhfEvidenceService;
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

    @Operation(summary = "生成DHF合规证据包")
    @PostMapping("/dhf")
    public Result<Map<String, Object>> generateDhf(@RequestParam Long projectId) {
        return Result.success(dhfEvidenceService.generateDhfPackage(projectId));
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