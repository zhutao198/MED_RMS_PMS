package com.zhutao.medrms.project.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * R211 v1.67: IPD 阶段门跨模块统计服务（FR-2.5）
 *
 * 跨模块聚合统计（用 JdbcTemplate 避免循环依赖，CLAUDE.md 项目铁律）：
 *  - 需求统计（req_schema.t_requirement）
 *  - 风险统计（risk_schema.t_risk_assessment）
 *  - IEC 62304 合规统计（compliance_schema.t_iec62304_checklist）
 *  - DHF 证据统计（compliance_schema.t_dhf_evidence）
 *  - SOUP 统计（compliance_schema.t_soup_component）
 *
 * 输入：projectId
 * 输出：所有校验统计聚合 Map
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IpdGateStatisticsService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 一次查询返回所有 IPD 阶段门校验统计
     */
    public Map<String, Integer> collectAll(Long projectId) {
        Map<String, Integer> stats = new HashMap<>();
        if (projectId == null) {
            return stats;
        }

        // 1. 需求统计
        stats.put("requirementCount", countRequirements(projectId));
        stats.put("approvedRequirementCount", countApprovedRequirements(projectId));
        stats.put("ursCount", countRequirementsByType(projectId, "URS"));
        stats.put("prsCount", countRequirementsByType(projectId, "PRS"));
        stats.put("srsCount", countRequirementsByType(projectId, "SRS"));
        stats.put("drsCount", countRequirementsByType(projectId, "DRS"));
        stats.put("drsImplementedCount", countDrsImplemented(projectId));

        // 2. 风险统计
        stats.put("riskCount", countRisks(projectId));
        stats.put("highRiskCount", countHighRisks(projectId));

        // 3. IEC 62304 合规
        stats.put("totalIecItems", countIecItems(projectId));
        stats.put("iecCompliantCount", countIecCompliant(projectId));

        // 4. DHF 证据
        stats.put("dhfEvidenceCount", countDhfEvidence(projectId));

        // 5. SOUP
        stats.put("soupCount", countSoup(projectId));

        log.debug("R211 IPD 统计聚合: projectId={}, {}", projectId, stats);
        return stats;
    }

    private int countRequirements(Long projectId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM req_schema.t_requirement WHERE project_id = ? AND is_deleted = false",
            Integer.class, projectId);
    }

    private int countRequirementsByType(Long projectId, String type) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM req_schema.t_requirement WHERE project_id = ? AND requirement_type = ? AND is_deleted = false",
            Integer.class, projectId, type);
    }

    private int countApprovedRequirements(Long projectId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM req_schema.t_requirement WHERE project_id = ? AND status IN ('Approved','Verified','Baseline') AND is_deleted = false",
            Integer.class, projectId);
    }

    private int countDrsImplemented(Long projectId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM req_schema.t_requirement WHERE project_id = ? AND requirement_type = 'DRS' AND status IN ('InProgress','InTest','Verified','Closed','Implemented') AND is_deleted = false",
            Integer.class, projectId);
    }

    private int countRisks(Long projectId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM risk_schema.t_risk_assessment WHERE project_id = ? AND is_deleted = false",
                Integer.class, projectId);
        } catch (Exception e) {
            // 兼容：projectId 可能不在 risk 表，使用 requirement_id 关联
            return jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT ra.id) FROM risk_schema.t_risk_assessment ra " +
                "JOIN req_schema.t_requirement r ON r.id = ra.requirement_id " +
                "WHERE r.project_id = ? AND ra.is_deleted = false",
                Integer.class, projectId);
        }
    }

    private int countHighRisks(Long projectId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM risk_schema.t_risk_assessment WHERE project_id = ? AND risk_level = 'HIGH' AND residual_risk NOT IN ('ACCEPTABLE','ALARP') AND is_deleted = false",
                Integer.class, projectId);
        } catch (Exception e) {
            return jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT ra.id) FROM risk_schema.t_risk_assessment ra " +
                "JOIN req_schema.t_requirement r ON r.id = ra.requirement_id " +
                "WHERE r.project_id = ? AND ra.risk_level = 'HIGH' AND ra.residual_risk NOT IN ('ACCEPTABLE','ALARP') AND ra.is_deleted = false",
                Integer.class, projectId);
        }
    }

    private int countIecItems(Long projectId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM compliance_schema.t_iec62304_checklist WHERE project_id = ?",
            Integer.class, projectId);
    }

    private int countIecCompliant(Long projectId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM compliance_schema.t_iec62304_checklist WHERE project_id = ? AND compliance_status = 'COMPLIANT'",
            Integer.class, projectId);
    }

    private int countDhfEvidence(Long projectId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM compliance_schema.t_dhf_evidence WHERE project_id = ? AND is_deleted = false",
            Integer.class, projectId);
    }

    private int countSoup(Long projectId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM compliance_schema.t_soup_component WHERE project_id = ? AND is_deleted = false",
                Integer.class, projectId);
        } catch (Exception e) {
            // SOUP 表可能不存在（如历史数据），返回 0
            log.debug("R211 SOUP 表查询失败（fail-safe）: {}", e.getMessage());
            return 0;
        }
    }
}
