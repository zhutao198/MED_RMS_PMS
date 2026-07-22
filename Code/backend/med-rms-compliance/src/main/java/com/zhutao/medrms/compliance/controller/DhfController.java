package com.zhutao.medrms.compliance.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.compliance.service.DhfEvidenceService;
import com.zhutao.medrms.compliance.service.DhfPdfRenderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * R207: DHF 证据包专用 Controller（URL 一致性修复）
 *
 * 路径：/compliance/dhf/{manifest, generate, download}
 * 解决 R119 已知问题：前端 DhfPackage.vue 调用 /compliance/dhf/*，
 *                    后端 ReportController 路径为 /reports/dhf。
 * 此 Controller 用前端期望路径，避免改前端代码。
 *
 * 关系：
 *  - 新端点（/compliance/dhf/*）：正式，与前端对齐
 *  - 老端点（/reports/dhf）：保留向后兼容（ReportController），下版本废弃
 */
@Slf4j
@Tag(name = "DHF 证据包", description = "DHF 设计历史文件证据包（FR-1.4）")
@RestController
@RequestMapping("/compliance/dhf")
@RequiredArgsConstructor
public class DhfController {

    private final DhfEvidenceService dhfEvidenceService;
    private final DhfPdfRenderService dhfPdfRenderService;

    /**
     * 获取 DHF 证据包清单（manifest，章节概览）
     */
    @Operation(summary = "DHF 证据包清单（章节概览）")
    @GetMapping("/manifest/{projectId}")
    public Result<Map<String, Object>> getManifest(@PathVariable Long projectId) {
        log.info("R207: 获取 DHF manifest, projectId={}", projectId);
        return Result.success(dhfEvidenceService.getDhfManifest(projectId));
    }

    /**
     * 生成 DHF 证据包 JSON 数据（前端预览）
     */
    @Operation(summary = "生成 DHF 证据包 JSON（前端预览）")
    @PostMapping("/generate/{projectId}")
    public Result<Map<String, Object>> generate(@PathVariable Long projectId) {
        log.info("R207: 生成 DHF JSON, projectId={}", projectId);
        return Result.success(dhfEvidenceService.generateDhfPackage(projectId));
    }

    /**
     * 下载 DHF 证据包 PDF（R207 核心端点）
     * 文件名：DHF-证据包-{projectNo}-{DCP阶段}-{yyyyMMdd}.pdf
     */
    @Operation(summary = "下载 DHF 证据包 PDF")
    @GetMapping("/download/{projectId}")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long projectId) {
        log.info("R207: 下载 DHF PDF, projectId={}", projectId);
        Map<String, Object> pkg = dhfEvidenceService.generateDhfPackage(projectId);
        byte[] pdf = dhfPdfRenderService.renderDhfPdf(pkg);
        String fileName = dhfPdfRenderService.buildFileName(pkg);
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", encoded);
        headers.setContentLength(pdf.length);
        headers.add("X-DHF-Status", String.valueOf(pkg.get("status")));
        headers.add("X-DHF-PackageId", String.valueOf(pkg.get("packageId")));
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
