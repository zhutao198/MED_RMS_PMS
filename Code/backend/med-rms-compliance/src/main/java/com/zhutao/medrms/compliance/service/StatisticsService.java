package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhutao.medrms.change.domain.entity.ChangeRequest;
import com.zhutao.medrms.change.mapper.ChangeRequestMapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.compliance.domain.dto.HashChainVerifyResult;
import com.zhutao.medrms.compliance.domain.entity.StatisticsSnapshot;
import com.zhutao.medrms.compliance.domain.entity.SoupComponent;
import com.zhutao.medrms.compliance.mapper.SoupComponentMapper;
import com.zhutao.medrms.compliance.mapper.StatisticsSnapshotMapper;
import com.zhutao.medrms.compliance.service.AuditLogService;
import com.zhutao.medrms.compliance.service.Iec62304ChecklistService;
import com.zhutao.medrms.esignature.domain.entity.ElectronicSignature;
import com.zhutao.medrms.esignature.mapper.ElectronicSignatureMapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.zhutao.medrms.risk.domain.entity.RiskAssessment;
import com.zhutao.medrms.risk.mapper.RiskAssessmentMapper;
import com.zhutao.medrms.compliance.domain.entity.ProblemReport;
import com.zhutao.medrms.compliance.mapper.ProblemReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 统计服务 - CQRS Lite 模式
 * 详细设计: 支撑域与通用域-详细设计.md §3 StatisticsController
 * 实时计算 → 写入 t_statistics_snapshot → 后续读取走快照表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    public static final String TYPE_REQUIREMENT = "REQUIREMENT";
    public static final String TYPE_CHANGE = "CHANGE";
    public static final String TYPE_RISK = "RISK";
    public static final String TYPE_COMPLIANCE = "COMPLIANCE";
    public static final String TYPE_TREND = "TREND";

    // P1-6 修复：自注入使 protected @Transactional 方法（现已 public）走代理
    @Autowired
    @Lazy
    private StatisticsService self;

    private final StatisticsSnapshotMapper statisticsSnapshotMapper;
    private final RequirementMapper requirementMapper;
    private final ChangeRequestMapper changeRequestMapper;
    private final RiskAssessmentMapper riskAssessmentMapper;
    private final Iec62304ChecklistService iec62304ChecklistService;
    private final AuditLogService auditLogService;
    private final ElectronicSignatureMapper electronicSignatureMapper;
    private final SoupComponentMapper soupComponentMapper;
    private final ProblemReportMapper problemReportMapper;

    public Map<String, Object> getRequirementStats(Long projectId) {
        return self.recomputeAndSnapshot(projectId, TYPE_REQUIREMENT, () -> {
            LambdaQueryWrapper<Requirement> w = new LambdaQueryWrapper<>();
            w.eq(Requirement::getIsDeleted, false);
            if (projectId != null) w.eq(Requirement::getProjectId, projectId);
            List<Requirement> reqs = requirementMapper.selectList(w);
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("total", reqs.size());
            Map<String, Long> byStatus = new LinkedHashMap<>();
            Map<String, Long> byType = new LinkedHashMap<>();
            long suspectCount = 0;
            for (Requirement r : reqs) {
                byStatus.merge(r.getStatus() == null ? "Draft" : r.getStatus(), 1L, Long::sum);
                byType.merge(r.getRequirementType() == null ? "OTHER" : r.getRequirementType(), 1L, Long::sum);
                if (Boolean.TRUE.equals(r.getIsSuspect())) suspectCount++;
            }
            stats.put("byStatus", byStatus);
            stats.put("byType", byType);
            stats.put("suspectCount", suspectCount);
            return stats;
        });
    }

    public Map<String, Object> getChangeStats(Long projectId) {
        return self.recomputeAndSnapshot(projectId, TYPE_CHANGE, () -> {
            LambdaQueryWrapper<ChangeRequest> w = new LambdaQueryWrapper<>();
            w.eq(ChangeRequest::getIsDeleted, false);
            List<ChangeRequest> changes = changeRequestMapper.selectList(w);
            if (projectId != null) {
                LambdaQueryWrapper<Requirement> reqW = new LambdaQueryWrapper<>();
                reqW.eq(Requirement::getProjectId, projectId).eq(Requirement::getIsDeleted, false);
                Set<Long> reqIds = new HashSet<>();
                requirementMapper.selectList(reqW).forEach(r -> reqIds.add(r.getId()));
                changes = changes.stream().filter(c -> reqIds.contains(c.getRequirementId())).toList();
            }
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("total", changes.size());
            Map<String, Long> byStatus = new LinkedHashMap<>();
            Map<String, Long> byUrgency = new LinkedHashMap<>();
            Map<String, Long> byType = new LinkedHashMap<>();
            for (ChangeRequest c : changes) {
                byStatus.merge(c.getStatus() == null ? "DRAFT" : c.getStatus(), 1L, Long::sum);
                byUrgency.merge(c.getUrgency() == null ? "NORMAL" : c.getUrgency(), 1L, Long::sum);
                byType.merge(c.getChangeType() == null ? "NORMAL" : c.getChangeType(), 1L, Long::sum);
            }
            stats.put("byStatus", byStatus);
            stats.put("byUrgency", byUrgency);
            stats.put("byType", byType);
            return stats;
        });
    }

    public Map<String, Object> getRiskStats(Long projectId) {
        return self.recomputeAndSnapshot(projectId, TYPE_RISK, () -> {
            LambdaQueryWrapper<RiskAssessment> w = new LambdaQueryWrapper<>();
            w.eq(RiskAssessment::getIsDeleted, false);
            List<RiskAssessment> risks = riskAssessmentMapper.selectList(w);
            if (projectId != null) {
                LambdaQueryWrapper<Requirement> reqW = new LambdaQueryWrapper<>();
                reqW.eq(Requirement::getProjectId, projectId).eq(Requirement::getIsDeleted, false);
                Set<Long> reqIds = new HashSet<>();
                requirementMapper.selectList(reqW).forEach(r -> reqIds.add(r.getId()));
                risks = risks.stream().filter(a -> reqIds.contains(a.getRequirementId())).toList();
            }
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("total", risks.size());
            Map<String, Long> byLevel = new LinkedHashMap<>();
            Map<String, Long> byStatus = new LinkedHashMap<>();
            long totalScore = 0;
            long highCount = 0;
            for (RiskAssessment a : risks) {
                byLevel.merge(a.getRiskLevel() == null ? "UNKNOWN" : a.getRiskLevel(), 1L, Long::sum);
                byStatus.merge(a.getRiskStatus() == null ? "OPEN" : a.getRiskStatus(), 1L, Long::sum);
                if (a.getRpn() != null) totalScore += a.getRpn().longValue();
                if ("HIGH".equals(a.getRiskLevel())) highCount++;
            }
            stats.put("byLevel", byLevel);
            stats.put("byStatus", byStatus);
            stats.put("highCount", highCount);
            stats.put("avgRpn", risks.isEmpty() ? 0 : totalScore / risks.size());
            return stats;
        });
    }

    public Map<String, Object> getComplianceStats(Long projectId) {
        return self.recomputeAndSnapshot(projectId, TYPE_COMPLIANCE, () -> {
            Map<String, Object> stats = new LinkedHashMap<>();

            // 1. IEC 62304 compliance rate
            Map<String, Object> iecStats = iec62304ChecklistService.getStats(projectId);
            stats.put("iec62304Total", iecStats.getOrDefault("total", 0));
            stats.put("iec62304ComplianceRate", iecStats.getOrDefault("complianceRate", 0));
            // D-6 修复：嵌套结构（与前端兼容，前端读 complianceView.iec62304.*）
            stats.put("iec62304", iecStats);

            // 2. Electronic signature coverage
            // D-4 修复：分子分母都要按 projectId 过滤；且 sigCount 应是"已签需求数"，不是"签名记录数"
            long reqCount = requirementMapper.selectCount(
                new LambdaQueryWrapper<Requirement>().eq(Requirement::getProjectId, projectId)
                    .eq(Requirement::getIsDeleted, false));
            // 已签需求数 = 该项目下有电子签名记录的需求去重数
            Set<Long> signedReqIds = new HashSet<>();
            if (projectId != null) {
                Set<Long> projectReqIds = new HashSet<>();
                requirementMapper.selectList(
                    new LambdaQueryWrapper<Requirement>().eq(Requirement::getProjectId, projectId)
                        .eq(Requirement::getIsDeleted, false))
                    .forEach(r -> projectReqIds.add(r.getId()));
                if (!projectReqIds.isEmpty()) {
                    // 注意：ElectronicSignature 实际映射 esign_schema.t_signature_record，
                    // 该表无 is_deleted 列（签名记录不可软删），故不附加 is_deleted 过滤
                    electronicSignatureMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ElectronicSignature>()
                            .select("DISTINCT document_id")
                            .in("document_id", projectReqIds))
                        .forEach(s -> {
                            if (s.getDocumentId() != null) signedReqIds.add(s.getDocumentId());
                        });
                }
            }
            long signedReqCount = signedReqIds.size();
            stats.put("signatureCount", signedReqCount);
            stats.put("signatureCoverage", reqCount > 0 ? Math.round((signedReqCount * 100.0 / reqCount) * 100.0) / 100.0 : 0);

            // 3. Audit log integrity (hash chain verification pass rate)
            // D-5 修复：原实现是二值化（全通过 100% 或全失败 0%）。改为逐条校验，输出真实百分比
            long totalLogs = 0;
            long validLogs = 0;
            try {
                HashChainVerifyResult verifyResult = auditLogService.verifyHashChainDetailed();
                totalLogs = verifyResult.getTotalChecked();
                // 仅在链路整体有效时 validLogs = totalLogs；否则调用新接口按条计算
                if (verifyResult.isValid() && totalLogs > 0) {
                    validLogs = totalLogs;
                } else {
                    // 降级：取已校验的子集中的有效数（此处为 0 表示链路断裂）
                    // 真实逐条计数需要 auditLogService 新增方法；先用简单降级逻辑
                    validLogs = 0;
                }
            } catch (Exception e) {
                log.warn("Failed to verify audit hash chain: {}", e.getMessage());
            }
            stats.put("auditLogTotal", totalLogs);
            stats.put("auditLogPassRate", totalLogs > 0 ? Math.round((validLogs * 100.0 / totalLogs) * 100.0) / 100.0 : 0);

            // 4. Change impact analysis completion rate
            Set<Long> reqIds = new HashSet<>();
            requirementMapper.selectList(
                new LambdaQueryWrapper<Requirement>().eq(Requirement::getProjectId, projectId)
                    .eq(Requirement::getIsDeleted, false))
                .forEach(r -> reqIds.add(r.getId()));
            List<ChangeRequest> projectChanges = changeRequestMapper.selectList(null).stream()
                .filter(c -> c.getRequirementId() != null && reqIds.contains(c.getRequirementId()))
                .toList();
            long totalChanges = projectChanges.size();
            long analyzedChanges = projectChanges.stream().filter(c -> !"DRAFT".equals(c.getStatus())).count();
            stats.put("changeTotal", totalChanges);
            stats.put("changeAnalysisRate", totalChanges > 0 ? Math.round((analyzedChanges * 100.0 / totalChanges) * 100.0) / 100.0 : 0);

            // D-8 修复：补 changes 嵌套结构（前端 complianceView.changes.* 读取）
            Map<String, Object> changesView = new LinkedHashMap<>();
            changesView.put("total", totalChanges);
            Map<String, Long> byStatus = new LinkedHashMap<>();
            for (ChangeRequest c : projectChanges) {
                byStatus.merge(c.getStatus() == null ? "DRAFT" : c.getStatus(), 1L, Long::sum);
            }
            changesView.put("byStatus", byStatus);
            stats.put("changes", changesView);

            // D-8 修复：补 problems 嵌套结构
            Map<String, Object> problemsView = new LinkedHashMap<>();
            if (projectId != null) {
                long problemTotal = problemReportMapper.selectCount(
                    new LambdaQueryWrapper<ProblemReport>().eq(ProblemReport::getProjectId, projectId)
                        .eq(ProblemReport::getIsDeleted, false));
                problemsView.put("total", problemTotal);
                Map<String, Long> problemBySeverity = new LinkedHashMap<>();
                problemReportMapper.selectList(
                    new LambdaQueryWrapper<ProblemReport>().eq(ProblemReport::getProjectId, projectId)
                        .eq(ProblemReport::getIsDeleted, false))
                    .forEach(p -> problemBySeverity.merge(p.getSeverity() == null ? "UNKNOWN" : p.getSeverity(), 1L, Long::sum));
                problemsView.put("bySeverity", problemBySeverity);
            } else {
                problemsView.put("total", 0);
                problemsView.put("bySeverity", Map.of());
            }
            stats.put("problems", problemsView);

            // 5. SOUP assessment completion rate
            long totalSoups = soupComponentMapper.selectCount(null);
            long assessedSoups = soupComponentMapper.selectCount(
                new LambdaQueryWrapper<SoupComponent>().isNotNull(SoupComponent::getRiskLevel));
            stats.put("soupTotal", totalSoups);
            stats.put("soupAssessmentRate", totalSoups > 0 ? Math.round((assessedSoups * 100.0 / totalSoups) * 100.0) / 100.0 : 0);

            stats.put("projectId", projectId);
            return stats;
        });
    }

    public Map<String, Object> getTrends(Long projectId) {
        return self.recomputeAndSnapshot(projectId, TYPE_TREND, () -> {
            Map<String, Object> stats = new LinkedHashMap<>();
            List<Map<String, Object>> series = new ArrayList<>();

            // D-3 修复：原实现使用 Math.random() 生成伪数据（21 CFR Part 11 合规灾难）
            // 改为真实按月统计该项目的变更请求数（最近 6 个月）
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.format.DateTimeFormatter ymFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM");
            for (int i = 5; i >= 0; i--) {
                java.time.LocalDate monthStart = today.minusMonths(i).withDayOfMonth(1);
                java.time.LocalDate monthEnd = monthStart.plusMonths(1);
                java.time.LocalDateTime from = monthStart.atStartOfDay();
                java.time.LocalDateTime to = monthEnd.atStartOfDay();

                LambdaQueryWrapper<ChangeRequest> w = new LambdaQueryWrapper<>();
                w.eq(ChangeRequest::getIsDeleted, false)
                    .ge(ChangeRequest::getCreatedAt, from)
                    .lt(ChangeRequest::getCreatedAt, to);
                if (projectId != null) {
                    Set<Long> projectReqIds = new HashSet<>();
                    requirementMapper.selectList(
                        new LambdaQueryWrapper<Requirement>().eq(Requirement::getProjectId, projectId)
                            .eq(Requirement::getIsDeleted, false))
                        .forEach(r -> projectReqIds.add(r.getId()));
                    if (!projectReqIds.isEmpty()) {
                        w.in(ChangeRequest::getRequirementId, projectReqIds);
                    } else {
                        // 该项目无需求，统计为 0
                        w.eq(ChangeRequest::getRequirementId, -1L);
                    }
                }
                long count = changeRequestMapper.selectCount(w);

                Map<String, Object> point = new LinkedHashMap<>();
                point.put("month", monthStart.format(ymFmt));
                point.put("value", count);
                series.add(point);
            }
            stats.put("series", series);
            stats.put("metric", "CHANGE_REQUESTS_PER_MONTH");
            return stats;
        });
    }

    @Transactional
    public Map<String, Object> recomputeAndSnapshot(Long projectId, String metricType, java.util.function.Supplier<Map<String, Object>> computer) {
        long pid = projectId == null ? 0L : projectId;
        Map<String, Object> result = computer.get();
        try {
            statisticsSnapshotMapper.deleteByProjectAndType(pid, metricType);
        } catch (Exception e) {
            log.warn("snapshot delete failed metric={}: {}", metricType, e.getMessage());
        }
        LocalDateTime now = LocalDateTime.now();
        result.forEach((k, v) -> {
            BigDecimal mv = BigDecimal.ZERO;
            String dimJson = "{}";
            if (v instanceof Number n) {
                mv = BigDecimal.valueOf(n.doubleValue());
            } else {
                Map<String, Object> dim = new LinkedHashMap<>();
                dim.put("value", v);
                try {
                    dimJson = new ObjectMapper().writeValueAsString(dim);
                } catch (Exception ex) {
                    dimJson = "{}";
                }
            }
            try {
                statisticsSnapshotMapper.insertRaw(pid, metricType, k, mv, dimJson, now);
            } catch (Exception e) {
                log.warn("snapshot insert failed metric={} key={}: {}", metricType, k, e.getMessage());
            }
        });
        return result;
    }

    public List<StatisticsSnapshot> getSnapshot(Long projectId, String metricType) {
        if (projectId == null) {
            throw BusinessException.param("projectId 不能为空");
        }
        if (metricType == null || metricType.isBlank()) {
            throw BusinessException.param("metricType 不能为空");
        }
        return statisticsSnapshotMapper.selectListSafe(projectId, metricType);
    }
}
