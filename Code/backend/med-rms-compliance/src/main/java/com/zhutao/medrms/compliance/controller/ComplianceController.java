package com.zhutao.medrms.compliance.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.compliance.domain.dto.HashChainVerifyResult;
import com.zhutao.medrms.compliance.domain.entity.AuditLog;
import com.zhutao.medrms.compliance.domain.entity.ComplianceCheck;
import com.zhutao.medrms.compliance.domain.entity.DhfEvidence;
import com.zhutao.medrms.compliance.domain.entity.RegulatoryMapping;
import com.zhutao.medrms.compliance.domain.entity.ProblemReport;
import com.zhutao.medrms.compliance.domain.entity.Iec62304ChecklistItem;
import com.zhutao.medrms.compliance.service.AuditLogService;
import com.zhutao.medrms.compliance.service.ComplianceCheckService;
import com.zhutao.medrms.compliance.service.DhfEvidenceService;
import com.zhutao.medrms.compliance.service.ErpsExportService;
import com.zhutao.medrms.compliance.service.RegulatoryMappingService;
import com.zhutao.medrms.compliance.service.ProblemReportService;
import com.zhutao.medrms.compliance.service.ReportService;
import com.zhutao.medrms.compliance.service.Iec62304ChecklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "合规管理", description = "审计日志、合规检查、DHF证据、法规映射、问题报告接口")
@RestController
@RequestMapping("/compliance")
@RequiredArgsConstructor
public class ComplianceController {

    private final AuditLogService auditLogService;
    private final ComplianceCheckService complianceCheckService;
    private final DhfEvidenceService dhfEvidenceService;
    private final RegulatoryMappingService regulatoryMappingService;
    private final ProblemReportService problemReportService;
    private final ReportService reportService;
    private final Iec62304ChecklistService iec62304ChecklistService;
    private final ErpsExportService erpsExportService;

    @Operation(summary = "查询实体的审计日志")
    @GetMapping("/audit-logs/entity/{entityType}/{entityId}")
    public Result<List<AuditLog>> getAuditLogsForEntity(@PathVariable String entityType, @PathVariable Long entityId) {
        return Result.success(auditLogService.getAuditLogsForEntity(entityType, entityId));
    }

    @Operation(summary = "查询用户的操作日志")
    @GetMapping("/audit-logs/operator/{operatorId}")
    public Result<List<AuditLog>> getAuditLogsByOperator(@PathVariable Long operatorId) {
        return Result.success(auditLogService.getAuditLogsByOperator(operatorId));
    }

    @Operation(summary = "查询时间范围内的审计日志")
    @GetMapping("/audit-logs/time-range")
    public Result<List<AuditLog>> getAuditLogsByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(auditLogService.getAuditLogsByTimeRange(startTime, endTime));
    }

    @Operation(summary = "查询所有审计日志")
    @GetMapping("/audit-logs")
    public Result<List<AuditLog>> listAuditLogs(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return Result.success(auditLogService.listAuditLogs(eventType, entityType, entityId, operatorId, startTime, endTime, page, size));
    }

    @Operation(summary = "校验哈希链完整性")
    @PostMapping("/audit-logs/verify")
    public Result<Boolean> verifyHashChain() {
        return Result.success(auditLogService.verifyHashChain());
    }

    @Operation(summary = "校验哈希链完整性（详细诊断）")
    @GetMapping("/audit-logs/verify/detailed")
    public Result<HashChainVerifyResult> verifyHashChainDetailed() {
        return Result.success(auditLogService.verifyHashChainDetailed());
    }

    @Operation(summary = "从指定 ID 开始分段校验哈希链（用于跳过历史断裂点）")
    @GetMapping("/audit-logs/verify/from/{startId}")
    public Result<HashChainVerifyResult> verifyHashChainFrom(@PathVariable Long startId) {
        return Result.success(auditLogService.verifyHashChainFrom(startId));
    }

    @Operation(summary = "查询需求的合规检查记录")
    @GetMapping("/check/list/{requirementId}")
    public Result<List<ComplianceCheck>> listByRequirement(@PathVariable Long requirementId) {
        return Result.success(complianceCheckService.listByRequirement(requirementId));
    }

    @Operation(summary = "查询项目的合规检查记录")
    @GetMapping("/check/project/{projectId}")
    public Result<List<ComplianceCheck>> listByProject(@PathVariable Long projectId) {
        return Result.success(complianceCheckService.listByProject(projectId));
    }

    @Operation(summary = "创建合规检查记录")
    @PostMapping("/check")
    public Result<ComplianceCheck> createCheck(@RequestBody ComplianceCheck check) {
        return Result.success(complianceCheckService.createCheck(check));
    }

    @Operation(summary = "完成合规检查")
    @PostMapping("/check/{id}/complete")
    public Result<ComplianceCheck> completeCheck(@PathVariable Long id,
                                                  @RequestParam String checkResult,
                                                  @RequestParam(required = false) String remarks) {
        return Result.success(complianceCheckService.completeCheck(id, checkResult, remarks));
    }

    @Operation(summary = "查询项目DHF证据列表")
    @GetMapping("/evidence/{projectId}")
    public Result<List<DhfEvidence>> listEvidence(@PathVariable Long projectId) {
        return Result.success(complianceCheckService.listEvidenceByProject(projectId));
    }

    @Operation(summary = "上传DHF证据")
    @PostMapping("/evidence")
    public Result<DhfEvidence> uploadEvidence(@RequestBody DhfEvidence evidence) {
        return Result.success(complianceCheckService.uploadEvidence(evidence));
    }

    @Operation(summary = "删除DHF证据")
    @DeleteMapping("/evidence/{id}")
    public Result<Void> deleteEvidence(@PathVariable Long id) {
        complianceCheckService.deleteEvidence(id);
        return Result.success();
    }

    @Operation(summary = "生成DHF合规证据包")
    @PostMapping("/dhf/generate/{projectId}")
    public Result<Object> generateDhf(@PathVariable Long projectId) {
        return Result.success(dhfEvidenceService.generateDhfPackage(projectId));
    }

    @Operation(summary = "DHF 证据包清单（仅结构，FR-1.4）")
    @GetMapping("/dhf/manifest/{projectId}")
    public Result<Map<String, Object>> getDhfManifest(@PathVariable Long projectId) {
        return Result.success(dhfEvidenceService.getDhfManifest(projectId));
    }

    @Operation(summary = "下载 DHF 证据包 JSON（FR-1.4）")
    @GetMapping(value = "/dhf/download/{projectId}", produces = "application/json;charset=UTF-8")
    public org.springframework.http.ResponseEntity<String> downloadDhfPackage(@PathVariable Long projectId) {
        Map<String, Object> pkg = dhfEvidenceService.generateDhfPackage(projectId);
        String json = com.alibaba.fastjson2.JSON.toJSONString(pkg);
        String filename = "DHF-" + projectId + "-" + System.currentTimeMillis() + ".json";
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(json);
    }

    @Operation(summary = "查询法规映射列表")
    @GetMapping("/regulations")
    public Result<List<RegulatoryMapping>> getRegulations(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String regulationType) {
        if (regulationType != null && !regulationType.isEmpty()) {
            return Result.success(regulatoryMappingService.listByRegulationType(regulationType));
        }
        if (projectId != null) {
            return Result.success(regulatoryMappingService.listByProjectId(projectId));
        }
        return Result.success(List.of());
    }

    @Operation(summary = "创建法规映射")
    @PostMapping("/regulations")
    public Result<RegulatoryMapping> createRegulation(@RequestBody RegulatoryMapping mapping) {
        return Result.success(regulatoryMappingService.create(mapping));
    }

    @Operation(summary = "创建问题报告")
    @PostMapping("/problem-reports")
    public Result<ProblemReport> createProblemReport(@RequestBody ProblemReport report) {
        return Result.success(problemReportService.create(report));
    }

    @Operation(summary = "查询问题报告列表")
    @GetMapping("/problem-reports")
    public Result<Object> getProblemReports(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (projectId != null) {
            return Result.success(problemReportService.listByProjectId(projectId, page, size));
        }
        if (severity != null && !severity.isEmpty()) {
            return Result.success(problemReportService.listBySeverity(severity, page, size));
        }
        if (status != null && !status.isEmpty()) {
            return Result.success(problemReportService.listByStatus(status, page, size));
        }
        return Result.success(problemReportService.listAll(page, size));
    }

    @Operation(summary = "更新问题报告状态")
    @PutMapping("/problem-reports/{id}/status")
    public Result<ProblemReport> updateProblemReportStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String resolution) {
        return Result.success(problemReportService.updateStatus(id, status, resolution));
    }

    @Operation(summary = "导出审计日志CSV")
    @GetMapping("/audit-logs/export")
    public void exportAuditLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String entityType,
            HttpServletResponse response) throws Exception {
        List<AuditLog> logs = auditLogService.getLogsForExport(startTime, endTime, entityType);
        String csv = auditLogService.generateCsv(logs);
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=audit-logs.csv");
        response.getWriter().write(csv);
    }

    @Operation(summary = "生成追溯性报告")
    @GetMapping("/reports/traceability")
    public Result<Map<String, Object>> getTraceabilityReport(@RequestParam Long projectId) {
        return Result.success(reportService.generateReport("TRACEABILITY", projectId));
    }

    @Operation(summary = "生成审计追踪报告")
    @GetMapping("/reports/audit-trail")
    public Result<Map<String, Object>> getAuditTrailReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        Map<String, Object> report = new HashMap<>();
        report.put("reportType", "AUDIT_TRAIL");
        report.put("startTime", startTime != null ? startTime.toString() : null);
        report.put("endTime", endTime != null ? endTime.toString() : null);
        report.put("generatedAt", LocalDateTime.now().toString());
        report.put("totalLogs", auditLogService.getAuditLogsByTimeRange(
                startTime != null ? startTime : LocalDateTime.now().minusYears(1),
                endTime != null ? endTime : LocalDateTime.now()).size());
        return Result.success(report);
    }

    // ========== IEC 62304 合规检查清单（FR-0.15 / US-9） ==========

    @Operation(summary = "初始化项目 IEC 62304 清单模板（仅在该项目无条款时执行）")
    @PostMapping("/iec62304/checklist/{projectId}/init")
    public Result<Integer> initIec62304(@PathVariable Long projectId) {
        return Result.success(iec62304ChecklistService.initForProject(projectId));
    }

    @Operation(summary = "查询项目 IEC 62304 检查清单")
    @GetMapping("/iec62304/checklist/{projectId}")
    public Result<List<Iec62304ChecklistItem>> listIec62304(@PathVariable Long projectId) {
        return Result.success(iec62304ChecklistService.listByProject(projectId));
    }

    @Operation(summary = "评估 IEC 62304 条款")
    @PostMapping("/iec62304/checklist/{id}/assess")
    public Result<Iec62304ChecklistItem> assessIec62304(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String evidence,
            @RequestParam(required = false) String gaps,
            @RequestParam(required = false) Long assessorId,
            @RequestParam(required = false) String assessorName) {
        return Result.success(iec62304ChecklistService.assess(id, status, evidence, gaps, assessorId, assessorName));
    }

    @Operation(summary = "查询项目 IEC 62304 统计信息")
    @GetMapping("/iec62304/checklist/{projectId}/stats")
    public Result<Map<String, Object>> getIec62304Stats(@PathVariable Long projectId) {
        return Result.success(iec62304ChecklistService.getStats(projectId));
    }

    @Operation(summary = "一键合规检查（FR-0.15 自动扫描）")
    @PostMapping("/iec62304/checklist/{projectId}/run-full-check")
    public Result<Map<String, Object>> runFullCheck(@PathVariable Long projectId) {
        return Result.success(iec62304ChecklistService.runFullCheck(projectId));
    }

    // ========== FR-1.12 NMPA eRPS 报告导出 ==========

    @Operation(summary = "导出 NMPA eRPS 报告（结构化 JSON，FR-1.12）")
    @GetMapping("/erps/export/{projectId}")
    public Result<Map<String, Object>> exportErps(@PathVariable Long projectId) {
        return Result.success(erpsExportService.exportProject(projectId));
    }

    @Operation(summary = "下载 NMPA eRPS 报告 JSON 文件（FR-1.12）")
    @GetMapping(value = "/erps/download/{projectId}", produces = "application/json;charset=UTF-8")
    public org.springframework.http.ResponseEntity<String> downloadErps(@PathVariable Long projectId) {
        Map<String, Object> data = erpsExportService.exportProject(projectId);
        String json = com.alibaba.fastjson2.JSON.toJSONString(data);
        String filename = "eRPS-" + projectId + "-" + System.currentTimeMillis() + ".json";
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(json);
    }
}