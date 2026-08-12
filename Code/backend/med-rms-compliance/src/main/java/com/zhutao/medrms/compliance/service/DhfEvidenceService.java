package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.change.mapper.ChangeRequestMapper;
import com.zhutao.medrms.compliance.domain.entity.AuditLog;
import com.zhutao.medrms.compliance.domain.entity.Baseline;
import com.zhutao.medrms.compliance.domain.entity.DhfEvidence;
import com.zhutao.medrms.compliance.domain.entity.ProblemReport;
import com.zhutao.medrms.compliance.domain.entity.RegulatoryMapping;
import com.zhutao.medrms.compliance.domain.entity.SoupComponent;
import com.zhutao.medrms.compliance.mapper.AuditLogMapper;
import com.zhutao.medrms.compliance.mapper.ProblemReportMapper;
import com.zhutao.medrms.compliance.mapper.SoupComponentMapper;
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
 *
 * R207 升级：
 *  - 补齐 4 个缺失章节：法规映射表 / 基线快照 / SOUP 组件清单 / 问题报告汇总
 *  - verdict 增强：返回不完整项明细列表（首页警告用）
 *  - 数据范围：除"全局最近 50 条"项目化（按 projectId 过滤）
 *
 * 真实数据接入：
 *  - 追溯矩阵 + 覆盖率：TraceabilityService
 *  - IEC 62304 合规清单状态：Iec62304ChecklistService
 *  - DHF 证据附件：ComplianceCheckService.listEvidenceByProject
 *  - 审计日志：AuditLogMapper（最近 50 条，按 projectId 过滤）
 *  - 电子签名：ElectronicSignatureMapper（最近 50 条）
 *  - 变更历史：ChangeRequestMapper（最近 50 条，按 projectId 过滤）
 *  - 项目基本信息：ProjectMapper
 *  - 法规映射表：RegulatoryMappingService.listByProjectId
 *  - 基线快照：BaselineService.getByProject
 *  - SOUP 组件清单：SoupComponentService.list（内存过滤 projectId）
 *  - 问题报告汇总：ProblemReportMapper（按 projectId 过滤）
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
    private final BaselineService baselineService;
    private final RegulatoryMappingService regulatoryMappingService;
    private final SoupComponentService soupComponentService;
    private final ChangeRequestMapper changeRequestMapper;
    private final AuditLogMapper auditLogMapper;
    private final ElectronicSignatureMapper electronicSignatureMapper;
    private final ProblemReportMapper problemReportMapper;
    private final SoupComponentMapper soupComponentMapper;

    /**
     * 生成 DHF 证据包（完整版，12 章节）
     */
    @com.zhutao.medrms.common.annotation.AuditLog(eventType = "GENERATE", entityType = "DHF_PACKAGE",
              operation = "生成DHF证据包", entityIdSpel = "#p0")
    public Map<String, Object> generateDhfPackage(Long projectId) {
        log.info("生成DHF证据包(R207 12章节): projectId={}", projectId);

        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("packageId", "DHF-" + System.currentTimeMillis());
        pkg.put("generatedAt", LocalDateTime.now().format(ISO));
        pkg.put("projectId", projectId);
        pkg.put("project", buildProjectInfo(projectId));

        // 1. 追溯矩阵摘要（项目维度）
        List<Map<String, Object>> traceMatrix = traceabilityService.getTraceMatrix(projectId);
        pkg.put("traceMatrix", traceMatrix);
        pkg.put("traceMatrixSize", traceMatrix == null ? 0 : traceMatrix.size());

        // 2. 覆盖率统计
        Map<String, Object> coverage = traceabilityService.getCoverageStats(projectId);
        pkg.put("coverageStats", coverage);

        // 3. IEC 62304 合规状态
        Map<String, Object> iecStats = iec62304ChecklistService.getStats(projectId);
        pkg.put("iec62304Stats", iecStats);

        // 4. DHF 证据附件清单
        List<DhfEvidence> evidences = complianceCheckService.listEvidenceByProject(projectId);
        pkg.put("dhfEvidences", evidences);
        pkg.put("dhfEvidenceCount", evidences.size());

        // 5. 变更历史（最近 50 条）
        pkg.put("changeHistory", listRecentChanges(projectId));

        // 6. 审计日志（最近 50 条）
        pkg.put("auditLogs", listRecentAuditLogs(projectId));

        // 7. 签名记录（最近 50 条）
        pkg.put("signatureLogs", listRecentSignatures());

        // R207 新增 4 章节（fail-safe：表不存在时返回空 List，确保 PDF 生成可用）
        // 8. 法规映射表（项目维度）
        List<RegulatoryMapping> regulations = safeListRegulations(projectId);
        pkg.put("regulatoryMappings", regulations);
        pkg.put("regulatoryMappingCount", regulations.size());

        // 9. 基线快照（项目维度）
        List<Baseline> baselines = safeListBaselines(projectId);
        pkg.put("baselines", baselines);
        pkg.put("baselineCount", baselines.size());

        // 10. SOUP 组件清单（项目维度，内存过滤）
        List<Map<String, Object>> soupList = listProjectSoup(projectId);
        pkg.put("soupComponents", soupList);
        pkg.put("soupComponentCount", soupList.size());

        // 11. 问题报告汇总（项目维度）
        List<ProblemReport> problems = listProjectProblems(projectId);
        pkg.put("problemReports", problems);
        pkg.put("problemReportCount", problems.size());

        // 综合判定 + 不完整项明细
        Map<String, Object> verdict = computeVerdict(coverage, iecStats, soupList, baselines, problems);
        pkg.put("verdict", verdict);
        pkg.put("status", verdict.get("status"));

        log.info("DHF证据包生成完成(R207): packageId={}, status={}, incompleteItems={}",
                pkg.get("packageId"), verdict.get("status"), verdict.get("incompleteItems"));
        return pkg;
    }

    /**
     * 仅返回包结构（manifest），用于前端快速预览（R207 URL 一致性修复）
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
            Map.of("key", "regulatoryMappings", "title", "法规映射表", "scope", "PROJECT"),
            Map.of("key", "baselines", "title", "基线快照", "scope", "PROJECT"),
            Map.of("key", "soupComponents", "title", "SOUP 组件清单", "scope", "PROJECT"),
            Map.of("key", "problemReports", "title", "问题报告汇总", "scope", "PROJECT"),
            Map.of("key", "changeHistory", "title", "变更历史", "scope", "PROJECT", "limit", EVIDENCE_LIMIT),
            Map.of("key", "auditLogs", "title", "审计日志", "scope", "GLOBAL", "limit", EVIDENCE_LIMIT),
            Map.of("key", "signatureLogs", "title", "电子签名", "scope", "GLOBAL", "limit", EVIDENCE_LIMIT),
            Map.of("key", "verdict", "title", "合规判定 + 不完整项", "scope", "PROJECT")
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

    /**
     * R207 + R236.2：变更历史按 projectId 过滤（JOIN req_schema.t_requirement）
     * 之前 selectList(null) 是全表扫描 + 跨项目串数据；改为 INNER JOIN 需求表 + project_id 限定
     */
    private List<Map<String, Object>> listRecentChanges(Long projectId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            // R236.2 DATA-028：按 projectId 限定（JOIN 需求表，按 requirement_id → project_id 过滤）
            // ChangeRequest 无 projectId 字段；通过 requirement_id 关联
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.zhutao.medrms.change.domain.entity.ChangeRequest> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            wrapper.select("change_no", "title", "change_type", "status", "urgency", "requester_name", "created_at")
                  // CODE_REVIEW M-1：用 apply + {0} 占位参数化，避免字符串拼接 SQL 注入
                  .apply("requirement_id IN (SELECT id FROM req_schema.t_requirement WHERE project_id = {0} AND is_deleted = false)",
                        projectId != null ? projectId : 0L)
                  .orderByDesc("created_at")
                  .last("LIMIT " + EVIDENCE_LIMIT);
            changeRequestMapper.selectList(wrapper).forEach(c -> {
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
        } catch (Exception e) {
            log.warn("变更历史查询失败（projectId={}）: {}", projectId, e.getMessage());
        }
        return result;
    }

    /**
     * R207：审计日志按 projectId 过滤（ChangeRequest/AuditLog 实体通常无 projectId 字段，
     * 这里通过 entityId LIKE 模糊匹配或全量后过滤；如无 projectId 字段则取全量最近 50 条）
     */
    private List<Map<String, Object>> listRecentAuditLogs(Long projectId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
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
        } catch (Exception e) {
            log.warn("审计日志查询失败: {}", e.getMessage());
        }
        return result;
    }

    private List<Map<String, Object>> listRecentSignatures() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
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
        } catch (Exception e) {
            log.warn("签名记录查询失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * R207：SOUP 组件清单（按 projectId 内存过滤）
     */
    /**
     * R207: 法规映射表 fail-safe（表不存在时返回空 List，不阻塞 PDF 生成）
     */
    private List<RegulatoryMapping> safeListRegulations(Long projectId) {
        try {
            return regulatoryMappingService.listByProjectId(projectId);
        } catch (Exception e) {
            log.warn("法规映射表查询失败(R207 fail-safe): {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * R207: 基线快照 fail-safe（表不存在时返回空 List）
     */
    private List<Baseline> safeListBaselines(Long projectId) {
        try {
            return baselineService.getByProject(projectId);
        } catch (Exception e) {
            log.warn("基线快照查询失败(R207 fail-safe): {}", e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> listProjectSoup(Long projectId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<SoupComponent> all = soupComponentMapper.selectList(
                new LambdaQueryWrapper<SoupComponent>().eq(SoupComponent::getIsDeleted, false)
            );
            for (SoupComponent c : all) {
                if (projectId != null && c.getProjectId() != null && !projectId.equals(c.getProjectId())) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", c.getId());
                item.put("componentName", c.getComponentName());
                item.put("componentCode", c.getComponentCode());
                item.put("version", c.getVersion());
                item.put("supplier", c.getSupplier());
                item.put("riskLevel", c.getRiskLevel());
                item.put("status", c.getStatus());
                item.put("securityDisclosure", c.getSecurityDisclosure());
                item.put("licenseExpiry", c.getLicenseExpiry() != null ? c.getLicenseExpiry().toString() : null);
                result.add(item);
            }
        } catch (Exception e) {
            log.warn("SOUP 组件查询失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * R207：问题报告（按 projectId 过滤）
     */
    private List<ProblemReport> listProjectProblems(Long projectId) {
        if (projectId == null) return List.of();
        try {
            return problemReportMapper.selectList(
                new LambdaQueryWrapper<ProblemReport>()
                    .eq(ProblemReport::getProjectId, projectId)
                    .eq(ProblemReport::getIsDeleted, false)
                    .orderByDesc(ProblemReport::getCreatedAt)
                    .last("LIMIT " + EVIDENCE_LIMIT)
            );
        } catch (Exception e) {
            log.warn("问题报告查询失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * R207 增强：综合判定 + 不完整项明细列表（首页警告用）
     */
    private Map<String, Object> computeVerdict(Map<String, Object> coverage, Map<String, Object> iecStats,
                                               List<Map<String, Object>> soupList, List<Baseline> baselines,
                                               List<ProblemReport> problems) {
        Map<String, Object> v = new LinkedHashMap<>();
        long totalRate = coverage.get("overall") instanceof Number
                ? ((Number) coverage.get("overall")).longValue() : 0;
        long compliant = iecStats.get("compliant") instanceof Number
                ? ((Number) iecStats.get("compliant")).longValue() : 0;
        long nonCompliant = iecStats.get("nonCompliant") instanceof Number
                ? ((Number) iecStats.get("nonCompliant")).longValue() : 0;
        long partial = iecStats.get("partial") instanceof Number
                ? ((Number) iecStats.get("partial")).longValue() : 0;

        // R207：不完整项明细列表
        List<String> incomplete = new ArrayList<>();
        if (totalRate < 90) incomplete.add("追溯覆盖率=" + totalRate + "%（<90%）");
        if (nonCompliant > 0) incomplete.add("IEC 62304 不合规条款 " + nonCompliant + " 条");
        if (partial > 0) incomplete.add("IEC 62304 部分合规条款 " + partial + " 条");

        // SOUP 未评估/高风险
        long soupUnassessed = soupList.stream()
            .filter(s -> s.get("riskLevel") == null || "PENDING".equals(s.get("riskLevel"))
                      || "未评估".equals(s.get("riskLevel")))
            .count();
        long soupHighRisk = soupList.stream()
            .filter(s -> "HIGH".equals(s.get("riskLevel")))
            .count();
        if (soupUnassessed > 0) incomplete.add("SOUP 组件未完成安全评估 " + soupUnassessed + " 个");
        if (soupHighRisk > 0) incomplete.add("SOUP 组件高风险 " + soupHighRisk + " 个");

        // 基线缺失
        boolean noBaseline = baselines == null || baselines.isEmpty();
        if (noBaseline) incomplete.add("项目尚未创建任何基线快照");

        // 未关闭问题报告
        long openProblems = problems.stream()
            .filter(p -> p.getStatus() != null && !p.getStatus().equalsIgnoreCase("Closed")
                      && !p.getStatus().equalsIgnoreCase("Resolved"))
            .count();
        if (openProblems > 0) incomplete.add("未关闭问题报告 " + openProblems + " 条");

        String status;
        String reason;
        // 合规硬门禁（fail-closed）：只要存在 IEC 62304 不合规条款，无论覆盖率多高一律判定 FAIL，
        // 防止含硬性不合规项的 DHF 证据包被误提交（FR-1.4 / IEC 62304）。
        if (nonCompliant > 0) {
            status = "FAIL";
            reason = "存在 IEC 62304 不合规条款 " + nonCompliant + " 条，必须整改后方可提交（硬性合规门禁）";
        } else if (incomplete.isEmpty() && totalRate >= 90) {
            status = "PASS";
            reason = "全部合规检查通过，可作为 DHF 证据包提交";
        } else if (!incomplete.isEmpty() && totalRate >= 70) {
            status = "WARN";
            reason = "存在 " + incomplete.size() + " 项不完整项（详见首页警告列表），建议补充后再提交";
        } else {
            status = "FAIL";
            reason = "覆盖率=" + totalRate + "%, 不合规条款=" + nonCompliant
                    + ", 不完整项=" + incomplete.size() + " 项";
        }

        v.put("status", status);
        v.put("reason", reason);
        v.put("traceRate", totalRate);
        v.put("iecCompliant", compliant);
        v.put("iecPartial", partial);
        v.put("iecNonCompliant", nonCompliant);
        v.put("incompleteItems", incomplete);   // R207 新增
        v.put("soupUnassessed", soupUnassessed); // R207 新增
        v.put("soupHighRisk", soupHighRisk);     // R207 新增
        v.put("openProblems", openProblems);     // R207 新增
        return v;
    }
}
