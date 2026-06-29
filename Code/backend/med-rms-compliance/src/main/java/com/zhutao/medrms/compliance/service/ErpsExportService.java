package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.change.domain.entity.ChangeRequest;
import com.zhutao.medrms.change.mapper.ChangeRequestMapper;
import com.zhutao.medrms.compliance.domain.entity.ProblemReport;
import com.zhutao.medrms.compliance.domain.entity.SoupComponent;
import com.zhutao.medrms.compliance.mapper.ProblemReportMapper;
import com.zhutao.medrms.compliance.mapper.SoupComponentMapper;
import com.zhutao.medrms.project.domain.entity.Project;
import com.zhutao.medrms.project.mapper.ProjectMapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.zhutao.medrms.risk.domain.entity.RiskAssessment;
import com.zhutao.medrms.risk.mapper.RiskAssessmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * FR-1.12 NMPA eRPS 报告导出
 * 国家药监局医疗器械注册电子申报接口，输出结构化 XML 风格的 JSON 包。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpsExportService {

    private final ProjectMapper projectMapper;
    private final RequirementMapper requirementMapper;
    private final RiskAssessmentMapper riskAssessmentMapper;
    private final ChangeRequestMapper changeRequestMapper;
    private final ProblemReportMapper problemReportMapper;
    private final SoupComponentMapper soupComponentMapper;
    private final Iec62304ChecklistService iec62304ChecklistService;

    private static final DateTimeFormatter DF = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 导出指定项目的 eRPS 包
     */
    public Map<String, Object> exportProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            log.warn("eRPS export: project {} not found", projectId);
            return Map.of("error", "项目不存在", "projectId", projectId);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema", "NMPA-eRPS-CHINA-MEDICAL-DEVICE-v1");
        root.put("generatedAt", LocalDateTime.now().format(DF));
        root.put("generator", "Med-RMS PMS");

        // 1. 产品信息
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("projectNo", project.getProjectNo());
        product.put("projectName", project.getProjectName());
        product.put("description", project.getDescription());
        product.put("status", project.getStatus());
        product.put("managerName", project.getManagerName());
        product.put("startDate", project.getStartDate() == null ? null : project.getStartDate().toString());
        product.put("endDate", project.getEndDate() == null ? null : project.getEndDate().toString());
        root.put("productInfo", product);

        // 2. 软件描述 (CH5.2 软件)
        Map<String, Object> software = new LinkedHashMap<>();
        software.put("softwareName", project.getProjectName());
        software.put("versionNo", "v1.0.0");
        software.put("safetyLevel", determineSafetyLevel(projectId));
        software.put("requirementCount", countRequirements(projectId));
        software.put("soupList", buildSoupList(projectId));
        root.put("softwareDescription", software);

        // 3. 风险管理文件摘要 (CH5.3 风险管理)
        Map<String, Object> risk = new LinkedHashMap<>();
        List<RiskAssessment> risks = collectRisks(projectId);
        long high = risks.stream().filter(r -> "HIGH".equals(r.getRiskLevel())).count();
        long medium = risks.stream().filter(r -> "MEDIUM".equals(r.getRiskLevel())).count();
        long low = risks.stream().filter(r -> "LOW".equals(r.getRiskLevel())).count();
        long uncontrolled = risks.stream().filter(r -> "UNACCEPTABLE".equals(r.getResidualRisk())).count();
        risk.put("total", risks.size());
        risk.put("high", high);
        risk.put("medium", medium);
        risk.put("low", low);
        risk.put("uncontrolledResidual", uncontrolled);
        root.put("riskManagementSummary", risk);

        // 4. 需求规格与追溯
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("requirementCount", countRequirements(projectId));
        trace.put("byType", requirementByType(projectId));
        trace.put("byStatus", requirementByStatus(projectId));
        root.put("requirementTrace", trace);

        // 5. 变更控制 (CH5.4 变更控制)
        Map<String, Object> change = new LinkedHashMap<>();
        List<ChangeRequest> changes = collectChanges(projectId);
        Map<String, Long> changeByStatus = new LinkedHashMap<>();
        for (ChangeRequest c : changes) {
            changeByStatus.merge(c.getStatus() == null ? "DRAFT" : c.getStatus(), 1L, Long::sum);
        }
        change.put("total", changes.size());
        change.put("byStatus", changeByStatus);
        root.put("changeControl", change);

        // 6. 问题报告
        Map<String, Object> problem = new LinkedHashMap<>();
        Map<String, Long> probBySev = new LinkedHashMap<>();
        collectProblems(projectId).forEach(p ->
                probBySev.merge(p.getSeverity() == null ? "UNKNOWN" : p.getSeverity(), 1L, Long::sum));
        problem.put("bySeverity", probBySev);
        root.put("problemReports", problem);

        // 7. IEC 62304 软件生命周期
        root.put("iec62304Summary", iec62304ChecklistService.getStats(projectId));

        // 8. 校验和
        root.put("checksum", computeChecksum(root));

        return root;
    }

    private String determineSafetyLevel(Long projectId) {
        // 简化判定：若存在未关闭的高风险 → C 级；否则按 SOUP 数量
        List<RiskAssessment> risks = collectRisks(projectId);
        boolean hasUncontrolledHigh = risks.stream().anyMatch(r ->
                "HIGH".equals(r.getRiskLevel()) && !"ACCEPTABLE".equals(r.getResidualRisk()));
        if (hasUncontrolledHigh) return "C";
        List<SoupComponent> soups = collectSoups(projectId);
        if (soups.size() > 5) return "C";
        if (!soups.isEmpty()) return "B";
        return "A";
    }

    private long countRequirements(Long projectId) {
        LambdaQueryWrapper<Requirement> w = new LambdaQueryWrapper<>();
        w.eq(Requirement::getIsDeleted, false).eq(Requirement::getProjectId, projectId);
        return requirementMapper.selectCount(w);
    }

    private Map<String, Long> requirementByType(Long projectId) {
        LambdaQueryWrapper<Requirement> w = new LambdaQueryWrapper<>();
        w.eq(Requirement::getIsDeleted, false).eq(Requirement::getProjectId, projectId);
        Map<String, Long> map = new LinkedHashMap<>();
        for (Requirement r : requirementMapper.selectList(w)) {
            map.merge(r.getRequirementType() == null ? "OTHER" : r.getRequirementType(), 1L, Long::sum);
        }
        return map;
    }

    private Map<String, Long> requirementByStatus(Long projectId) {
        LambdaQueryWrapper<Requirement> w = new LambdaQueryWrapper<>();
        w.eq(Requirement::getIsDeleted, false).eq(Requirement::getProjectId, projectId);
        Map<String, Long> map = new LinkedHashMap<>();
        for (Requirement r : requirementMapper.selectList(w)) {
            map.merge(r.getStatus() == null ? "Draft" : r.getStatus(), 1L, Long::sum);
        }
        return map;
    }

    private List<RiskAssessment> collectRisks(Long projectId) {
        if (projectId == null) {
            return riskAssessmentMapper.selectList(
                    new LambdaQueryWrapper<RiskAssessment>().eq(RiskAssessment::getIsDeleted, false));
        }
        Set<Long> reqIds = new HashSet<>();
        LambdaQueryWrapper<Requirement> rw = new LambdaQueryWrapper<>();
        rw.eq(Requirement::getProjectId, projectId).eq(Requirement::getIsDeleted, false);
        requirementMapper.selectList(rw).forEach(r -> reqIds.add(r.getId()));
        return riskAssessmentMapper.selectList(
                new LambdaQueryWrapper<RiskAssessment>().eq(RiskAssessment::getIsDeleted, false))
                .stream().filter(a -> reqIds.contains(a.getRequirementId())).toList();
    }

    private List<ChangeRequest> collectChanges(Long projectId) {
        return changeRequestMapper.selectList(
                new LambdaQueryWrapper<ChangeRequest>().eq(ChangeRequest::getIsDeleted, false));
    }

    private List<ProblemReport> collectProblems(Long projectId) {
        return problemReportMapper.selectList(
                new LambdaQueryWrapper<ProblemReport>().eq(ProblemReport::getIsDeleted, false));
    }

    private List<SoupComponent> collectSoups(Long projectId) {
        return soupComponentMapper.selectList(
                new LambdaQueryWrapper<SoupComponent>().eq(SoupComponent::getIsDeleted, false));
    }

    private List<Map<String, Object>> buildSoupList(Long projectId) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SoupComponent s : collectSoups(projectId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("componentName", s.getComponentName());
            m.put("version", s.getVersion());
            m.put("supplier", s.getSupplier());
            m.put("riskLevel", s.getRiskLevel());
            list.add(m);
        }
        return list;
    }

    private String computeChecksum(Map<String, Object> data) {
        int hash = data.toString().hashCode();
        return String.format("%08X", hash & 0xFFFFFFFFL);
    }
}
