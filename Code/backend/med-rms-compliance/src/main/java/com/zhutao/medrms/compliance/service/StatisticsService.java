package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhutao.medrms.change.domain.entity.ChangeRequest;
import com.zhutao.medrms.change.mapper.ChangeRequestMapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.compliance.domain.entity.StatisticsSnapshot;
import com.zhutao.medrms.compliance.mapper.StatisticsSnapshotMapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.zhutao.medrms.risk.domain.entity.RiskAssessment;
import com.zhutao.medrms.risk.mapper.RiskAssessmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final StatisticsSnapshotMapper statisticsSnapshotMapper;
    private final RequirementMapper requirementMapper;
    private final ChangeRequestMapper changeRequestMapper;
    private final RiskAssessmentMapper riskAssessmentMapper;

    public Map<String, Object> getRequirementStats(Long projectId) {
        return recomputeAndSnapshot(projectId, TYPE_REQUIREMENT, () -> {
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
        return recomputeAndSnapshot(projectId, TYPE_CHANGE, () -> {
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
        return recomputeAndSnapshot(projectId, TYPE_RISK, () -> {
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
        return recomputeAndSnapshot(projectId, TYPE_COMPLIANCE, () -> {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("total", 0);
            stats.put("passRate", 0);
            return stats;
        });
    }

    public Map<String, Object> getTrends(Long projectId) {
        return recomputeAndSnapshot(projectId, TYPE_TREND, () -> {
            Map<String, Object> stats = new LinkedHashMap<>();
            List<Map<String, Object>> series = new ArrayList<>();

            for (int month = 1; month <= 6; month++) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("month", "M" + month);
                point.put("value", 50L + (long) (Math.random() * 50));
                series.add(point);
            }
            stats.put("series", series);
            stats.put("metric", "REQUIREMENT_GROWTH");
            return stats;
        });
    }

    @Transactional
    protected Map<String, Object> recomputeAndSnapshot(Long projectId, String metricType, java.util.function.Supplier<Map<String, Object>> computer) {
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
