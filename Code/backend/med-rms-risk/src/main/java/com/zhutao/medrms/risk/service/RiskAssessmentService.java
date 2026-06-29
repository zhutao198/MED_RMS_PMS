package com.zhutao.medrms.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.risk.domain.entity.RiskAssessment;
import com.zhutao.medrms.risk.mapper.RiskAssessmentMapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAssessmentService {

    private final RiskAssessmentMapper riskAssessmentMapper;
    private final RequirementMapper requirementMapper;

    @Transactional
    public RiskAssessment assess(Long requirementId, String riskLevel, String hazardLevel,
                                  String hazardSource, String hazardSituation, String harm,
                                  String controlMeasure, Long assessedBy) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw BusinessException.notFound("RQ0101", "需求不存在");
        }

        BigDecimal riskScore = calculateRiskScore(riskLevel, hazardLevel);

        RiskAssessment assessment = new RiskAssessment();
        assessment.setRequirementId(requirementId);
        assessment.setRiskLevel(riskLevel);
        assessment.setHazardLevel(hazardLevel);
        assessment.setRiskScore(riskScore);
        assessment.setHazardSource(hazardSource);
        assessment.setHazardSituation(hazardSituation);
        assessment.setHarm(harm);
        assessment.setControlMeasure(controlMeasure);
        assessment.setResidualRisk(calculateResidualRisk(riskScore, controlMeasure));
        assessment.setRiskStatus("OPEN");
        assessment.setAssessedBy(assessedBy);
        assessment.setAssessedAt(LocalDateTime.now());

        riskAssessmentMapper.insert(assessment);

        log.info("风险评估: requirementId={}, riskLevel={}, score={}", requirementId, riskLevel, riskScore);
        return assessment;
    }

    public List<RiskAssessment> getByRequirement(Long requirementId) {
        return riskAssessmentMapper.selectByRequirementId(requirementId);
    }

    public Map<String, Object> getRiskReport(Long projectId) {
        Map<String, Object> report = new HashMap<>();

        long total = riskAssessmentMapper.selectCount(
            new LambdaQueryWrapper<RiskAssessment>().eq(RiskAssessment::getIsDeleted, false));

        long high = riskAssessmentMapper.selectCount(
            new LambdaQueryWrapper<RiskAssessment>()
                .eq(RiskAssessment::getIsDeleted, false)
                .eq(RiskAssessment::getRiskLevel, "HIGH"));

        long medium = riskAssessmentMapper.selectCount(
            new LambdaQueryWrapper<RiskAssessment>()
                .eq(RiskAssessment::getIsDeleted, false)
                .eq(RiskAssessment::getRiskLevel, "MEDIUM"));

        long low = riskAssessmentMapper.selectCount(
            new LambdaQueryWrapper<RiskAssessment>()
                .eq(RiskAssessment::getIsDeleted, false)
                .eq(RiskAssessment::getRiskLevel, "LOW"));

        long uncontrolled = riskAssessmentMapper.selectCount(
            new LambdaQueryWrapper<RiskAssessment>()
                .eq(RiskAssessment::getIsDeleted, false)
                .eq(RiskAssessment::getResidualRisk, "UNACCEPTABLE"));

        report.put("totalRisks", total);
        report.put("highRisks", high);
        report.put("mediumRisks", medium);
        report.put("lowRisks", low);
        report.put("uncontrolledRisks", uncontrolled);

        return report;
    }

    @Transactional
    public RiskAssessment updateControlMeasure(Long riskId, String controlMeasure, Long reviewedBy) {
        RiskAssessment assessment = riskAssessmentMapper.selectById(riskId);
        if (assessment == null) {
            throw BusinessException.notFound("RK0101", "风险评估记录不存在");
        }

        assessment.setControlMeasure(controlMeasure);
        assessment.setResidualRisk(calculateResidualRisk(assessment.getRiskScore(), controlMeasure));
        assessment.setReviewedBy(reviewedBy);
        assessment.setReviewedAt(LocalDateTime.now());

        riskAssessmentMapper.updateById(assessment);

        log.info("更新控制措施: riskId={}, residualRisk={}", riskId, assessment.getResidualRisk());
        return assessment;
    }

    /**
     * FMEA 在线编辑：保存 S/O/D 后自动计算 RPN
     */
    @Transactional
    public RiskAssessment saveFmea(Long riskId, Integer severity, Integer occurrence, Integer detection,
                                   String actionPlan, String actionOwner, LocalDateTime actionDueDate) {
        RiskAssessment assessment = riskAssessmentMapper.selectById(riskId);
        if (assessment == null) {
            throw BusinessException.notFound("RK0101", "风险评估记录不存在");
        }
        validateFmeaScore("severity", severity);
        validateFmeaScore("occurrence", occurrence);
        validateFmeaScore("detection", detection);

        assessment.setSeverity(severity);
        assessment.setOccurrence(occurrence);
        assessment.setDetection(detection);
        assessment.setRpn(severity * occurrence * detection);
        assessment.setActionPlan(actionPlan);
        assessment.setActionOwner(actionOwner);
        assessment.setActionDueDate(actionDueDate);
        if (assessment.getActionStatus() == null) {
            assessment.setActionStatus("OPEN");
        }

        riskAssessmentMapper.updateById(assessment);
        log.info("FMEA 更新: riskId={}, RPN={}", riskId, assessment.getRpn());
        return assessment;
    }

    /**
     * 更新改进措施状态
     */
    @Transactional
    public RiskAssessment updateActionStatus(Long riskId, String actionStatus) {
        RiskAssessment assessment = riskAssessmentMapper.selectById(riskId);
        if (assessment == null) {
            throw BusinessException.notFound("RK0101", "风险评估记录不存在");
        }
        if (!List.of("OPEN", "IN_PROGRESS", "COMPLETED").contains(actionStatus)) {
            throw BusinessException.param("actionStatus 非法 [" + actionStatus + "]");
        }
        assessment.setActionStatus(actionStatus);
        riskAssessmentMapper.updateById(assessment);
        return assessment;
    }

    /**
     * FMEA 列表查询（按 RPN 降序，可选 RPN 阈值）
     */
    public List<RiskAssessment> listFmea(Integer rpnThreshold) {
        LambdaQueryWrapper<RiskAssessment> w = new LambdaQueryWrapper<>();
        w.eq(RiskAssessment::getIsDeleted, false)
                .isNotNull(RiskAssessment::getRpn)
                .orderByDesc(RiskAssessment::getRpn);
        if (rpnThreshold != null) {
            w.ge(RiskAssessment::getRpn, rpnThreshold);
        }
        return riskAssessmentMapper.selectList(w);
    }

    private void validateFmeaScore(String field, Integer value) {
        if (value == null || value < 1 || value > 10) {
            throw BusinessException.param(field + " 必须在 1-10 之间，当前=" + value);
        }
    }

    private BigDecimal calculateRiskScore(String riskLevel, String hazardLevel) {
        int riskValue = switch (riskLevel) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
        int hazardValue = switch (hazardLevel) {
            case "CATASTROPHIC" -> 5;
            case "CRITICAL" -> 4;
            case "MAJOR" -> 3;
            case "MINOR" -> 2;
            case "NEGLIGIBLE" -> 1;
            default -> 0;
        };
        return BigDecimal.valueOf(riskValue * hazardValue);
    }

    private String calculateResidualRisk(BigDecimal riskScore, String controlMeasure) {
        if (riskScore == null) return "UNACCEPTABLE";
        if (controlMeasure == null || controlMeasure.isBlank()) {
            return riskScore.compareTo(BigDecimal.valueOf(15)) > 0 ? "UNACCEPTABLE" : "ALARP";
        }
        return riskScore.compareTo(BigDecimal.valueOf(10)) > 0 ? "ALARP" : "ACCEPTABLE";
    }
}