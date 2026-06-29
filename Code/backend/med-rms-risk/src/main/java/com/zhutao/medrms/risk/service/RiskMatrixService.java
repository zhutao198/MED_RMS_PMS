package com.zhutao.medrms.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.util.SecurityUtils;
import com.zhutao.medrms.risk.domain.entity.RiskMatrix;
import com.zhutao.medrms.risk.mapper.RiskMatrixMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RiskMatrixService {

    private final RiskMatrixMapper riskMatrixMapper;

    public List<RiskMatrix> listByProject(Long projectId) {
        LambdaQueryWrapper<RiskMatrix> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RiskMatrix::getProjectId, projectId)
               .eq(RiskMatrix::getIsDeleted, false)
               .orderByDesc(RiskMatrix::getRpn);
        return riskMatrixMapper.selectList(wrapper);
    }

    public List<RiskMatrix> listByType(String matrixType) {
        LambdaQueryWrapper<RiskMatrix> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RiskMatrix::getMatrixType, matrixType)
               .eq(RiskMatrix::getIsDeleted, false)
               .orderByDesc(RiskMatrix::getRpn);
        return riskMatrixMapper.selectList(wrapper);
    }

    public RiskMatrix getById(Long id) {
        RiskMatrix matrix = riskMatrixMapper.selectById(id);
        if (matrix == null || matrix.getIsDeleted()) {
            throw BusinessException.notFound("RS0201", "风险矩阵项不存在");
        }
        return matrix;
    }

    @Transactional
    public RiskMatrix create(RiskMatrix matrix) {
        calculateRpn(matrix);
        LocalDateTime nowDt = LocalDateTime.now();
        matrix.setAssessedAt(nowDt);
        // 从 SecurityContext 注入评估人；未登录时保持前端传入值
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId != null && matrix.getAssessedBy() == null) {
            matrix.setAssessedBy(currentUserId);
        }
        riskMatrixMapper.insert(matrix);
        return matrix;
    }

    @Transactional
    public RiskMatrix update(Long id, RiskMatrix updates) {
        RiskMatrix matrix = getById(id);
        if (updates.getSeverity() != null) matrix.setSeverity(updates.getSeverity());
        if (updates.getProbability() != null) matrix.setProbability(updates.getProbability());
        if (updates.getDetectability() != null) matrix.setDetectability(updates.getDetectability());
        if (updates.getMitigationMeasure() != null) matrix.setMitigationMeasure(updates.getMitigationMeasure());
        calculateRpn(matrix);
        // 任何编辑都更新评估人/时间
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId != null) {
            matrix.setAssessedBy(currentUserId);
        }
        matrix.setAssessedAt(LocalDateTime.now());
        riskMatrixMapper.updateById(matrix);
        return matrix;
    }

    @Transactional
    public RiskMatrix calculateResidual(Long id, String mitigationMeasure) {
        RiskMatrix matrix = getById(id);
        matrix.setMitigationMeasure(mitigationMeasure);
        int s = Integer.parseInt(matrix.getSeverity());
        int p = Integer.parseInt(matrix.getProbability());
        int d = Integer.parseInt(matrix.getDetectability());
        BigDecimal residualRpn = new BigDecimal(s * p * d / 2);
        matrix.setResidualRpn(residualRpn);
        matrix.setResidualRisk(residualRpn.compareTo(new BigDecimal(50)) > 0 ? "HIGH" :
                            residualRpn.compareTo(new BigDecimal(20)) > 0 ? "MEDIUM" : "LOW");
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId != null) {
            matrix.setAssessedBy(currentUserId);
        }
        matrix.setAssessedAt(LocalDateTime.now());
        riskMatrixMapper.updateById(matrix);
        return matrix;
    }

    private void calculateRpn(RiskMatrix matrix) {
        int s = Integer.parseInt(matrix.getSeverity());
        int p = Integer.parseInt(matrix.getProbability());
        int d = Integer.parseInt(matrix.getDetectability());
        BigDecimal rpn = new BigDecimal(s * p * d);
        matrix.setRpn(rpn);
        matrix.setRiskLevel(rpn.compareTo(new BigDecimal(50)) > 0 ? "HIGH" :
                           rpn.compareTo(new BigDecimal(20)) > 0 ? "MEDIUM" : "LOW");
        matrix.setRiskZone(rpn.compareTo(new BigDecimal(50)) > 0 ? "RED" :
                           rpn.compareTo(new BigDecimal(20)) > 0 ? "YELLOW" : "GREEN");
    }
}