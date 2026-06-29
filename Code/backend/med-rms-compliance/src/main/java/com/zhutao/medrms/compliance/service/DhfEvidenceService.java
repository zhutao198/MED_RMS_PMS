package com.zhutao.medrms.compliance.service;

import com.zhutao.medrms.change.mapper.ChangeRequestMapper;
import com.zhutao.medrms.compliance.domain.entity.AuditLog;
import com.zhutao.medrms.compliance.domain.entity.DhfEvidence;
import com.zhutao.medrms.compliance.mapper.AuditLogMapper;
import com.zhutao.medrms.esignature.domain.entity.ElectronicSignature;
import com.zhutao.medrms.esignature.mapper.ElectronicSignatureMapper;
import com.zhutao.medrms.project.domain.entity.Project;
import com.zhutao.medrms.project.mapper.ProjectMapper;
import com.zhutao.medrms.traceability.service.TraceabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * DHF（Design History File）证据包生成服务 - FR-1.4
 * 真实数据接入：
 *  - 追溯矩阵 + 覆盖率：TraceabilityService
 *  - IEC 62304 合规清单状态：Iec62304ChecklistService
 *  - DHF 证据附件：ComplianceCheckService.listEvidenceByProject
 *  - 审计日志：AuditLogMapper（近 50 条，全系统）
 *  - 电子签名：ElectronicSignatureMapper（近 50 条，全系统）
 *  - 变更历史：ChangeRequestMapper（近 50 条，全系统）
 *  - 项目基本信息：ProjectMapper
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DhfEvidenceService {

    private static final int EVIDENCE_LIMIT = 50;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ProjectMapper projectMapper;
    private final TraceabilityService traceabilityService;
    private final Iec62304ChecklistService iec62304ChecklistService;
    private final ComplianceCheckService complianceCheckService;
    private final ChangeRequestMapper changeRequestMapper;
    private final AuditLogMapper auditLogMapper;
    private final ElectronicSignatureMapper electronicSignatureMapper;

    /**
     * 生成 DHF 证据包（完整版）
     */
    public Map<String, Object> generateDhfPackage(Long projectId) {
        log.info("生成DHF证据包: projectId={}", projectId);

        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("packageId", "DHF-" + System.currentTimeMillis());
        pkg.put("generatedAt", LocalDateTime.now().format(ISO));
        pkg.put("projectId", projectId);
        pkg.put("project", buildProjectInfo(projectId));

        // 1. 追溯矩阵摘要（项目维度）
        pkg.put("traceMatrix", traceabilityService.getTraceMatrix(projectId));
        pkg.put("traceMatrixSize", countMatrixRows(traceabilityService.getTraceMatrix(projectId)));

        // 2. 覆盖率统计（项目维度，来自 TraceabilityService.getCoverageStats）
        Map<String, Object> coverage = traceabilityService.getCoverageStats(projectId);
        pkg.put("coverageStats", coverage);

        // 3. IEC 62304 合规状态（项目维度）
        pkg.put("iec62304Stats", iec62304ChecklistService.getStats(projectId));

        // 4. DHF 证据附件清单（项目维度）
        List<DhfEvidence> evidences = complianceCheckService.listEvidenceByProject(projectId);
        pkg.put("dhfEvidences", evidences);
        pkg.put("dhfEvidenceCount", evidences.size());

        // 5. 变更历史（最近 50 条，全系统）
        pkg.put("changeHistory", listRecentChanges());

        // 6. 审计日志（最近 50 条，全系统）
        pkg.put("auditLogs", listRecentAuditLogs());

        // 7. 签名记录（最近 50 条，全系统）
        pkg.put("signatureLogs", listRecentSignatures());

        // 综合判定
        Map<String, Object> verdict = computeVerdict(coverage, iec62304ChecklistService.getStats(projectId));
        pkg.put("verdict", verdict);
        pkg.put("status", verdict.get("status"));

        log.info("DHF证据包生成完成: packageId={}, status={}", pkg.get("packageId"), verdict.get("status"));
        return pkg;
    }

    /**
     * 仅返回包结构（manifest），用于前端快速预览
     */
    public Map<String, Object> getDhfManifest(Long projectId) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("projectId", projectId);
        manifest.put("project", buildProjectInfo(projectId));
        manifest.put("sections", List.of(
            Map.of("key", "traceMatrix", "title", "追溯矩阵", "scope", "PROJECT"),
            Map.of("key", "coverageStats", "title", "覆盖率统计", "scope", "PROJECT"),
            Map.of("key", "iec62304Stats", "title", "IEC 62304 合规状态", "scope", "PROJECT"),
            Map.of("key", "dhfEvidences", "title", "DHF 证据附件", "scope", "PROJECT"),
            Map.of("key", "changeHistory", "title", "变更历史", "scope", "GLOBAL", "limit", EVIDENCE_LIMIT),
            Map.of("key", "auditLogs", "title", "审计日志", "scope", "GLOBAL", "limit", EVIDENCE_LIMIT),
            Map.of("key", "signatureLogs", "title", "电子签名", "scope", "GLOBAL", "limit", EVIDENCE_LIMIT)
        ));
        return manifest;
    }

    private Map<String, Object> buildProjectInfo(Long projectId) {
        Project p = projectMapper.selectById(projectId);
        if (p == null) {
            return Map.of("projectId", projectId, "projectName", "未知项目");
        }
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("projectId", p.getId());
        info.put("projectNo", p.getProjectNo());
        info.put("projectName", p.getProjectName());
        info.put("description", p.getDescription());
        info.put("status", p.getStatus());
        info.put("templateCode", p.getTemplateCode());
        return info;
    }

    private int countMatrixRows(List<Map<String, Object>> matrix) {
        return matrix == null ? 0 : matrix.size();
    }

    private List<Map<String, Object>> listRecentChanges() {
        List<Map<String, Object>> result = new ArrayList<>();
        changeRequestMapper.selectList(null).stream().limit(EVIDENCE_LIMIT).forEach(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("changeNo", c.getChangeNo());
            item.put("title", c.getTitle());
            item.put("changeType", c.getChangeType());
            item.put("status", c.getStatus());
            item.put("urgency", c.getUrgency());
            item.put("requesterName", c.getRequesterName());
            item.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().format(ISO) : null);
            result.add(item);
        });
        return result;
    }

    private List<Map<String, Object>> listRecentAuditLogs() {
        List<Map<String, Object>> result = new ArrayList<>();
        auditLogMapper.selectList(null).stream().limit(EVIDENCE_LIMIT).forEach(a -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("eventType", a.getEventType());
            item.put("entityType", a.getEntityType());
            item.put("entityId", a.getEntityId());
            item.put("operatorName", a.getOperatorName());
            item.put("operation", a.getOperation());
            item.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().format(ISO) : null);
            result.add(item);
        });
        return result;
    }

    private List<Map<String, Object>> listRecentSignatures() {
        List<Map<String, Object>> result = new ArrayList<>();
        electronicSignatureMapper.selectList(null).stream().limit(EVIDENCE_LIMIT).forEach(s -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("signerName", s.getSignerName());
            item.put("signatureType", s.getSignatureType());
            item.put("documentType", s.getDocumentType());
            item.put("documentId", s.getDocumentId());
            item.put("signedAt", s.getSignedAt() != null ? s.getSignedAt().format(ISO) : null);
            item.put("signatureHash", s.getSignatureHash());
            result.add(item);
        });
        return result;
    }

    private Map<String, Object> computeVerdict(Map<String, Object> coverage, Map<String, Object> iecStats) {
        Map<String, Object> v = new LinkedHashMap<>();
        long totalRate = coverage.get("overall") instanceof Number
                ? ((Number) coverage.get("overall")).longValue() : 0;
        long compliant = iecStats.get("compliant") instanceof Number
                ? ((Number) iecStats.get("compliant")).longValue() : 0;
        long nonCompliant = iecStats.get("nonCompliant") instanceof Number
                ? ((Number) iecStats.get("nonCompliant")).longValue() : 0;
        long partial = iecStats.get("partial") instanceof Number
                ? ((Number) iecStats.get("partial")).longValue() : 0;

        String status;
        String reason;
        if (totalRate >= 90 && nonCompliant == 0) {
            status = "PASS";
            reason = "追溯覆盖率≥90% 且无严重不合规条款";
        } else if (totalRate >= 70 && nonCompliant == 0) {
            status = "WARN";
            reason = "覆盖率≥70% 但建议补充证据（覆盖率=" + totalRate + "%）";
        } else {
            status = "FAIL";
            reason = "覆盖率=" + totalRate + "%, 不合规条款=" + nonCompliant
                    + ", 部分合规=" + partial + ", 完全合规=" + compliant;
        }
        v.put("status", status);
        v.put("reason", reason);
        v.put("traceRate", totalRate);
        v.put("iecCompliant", compliant);
        v.put("iecPartial", partial);
        v.put("iecNonCompliant", nonCompliant);
        return v;
    }
}
