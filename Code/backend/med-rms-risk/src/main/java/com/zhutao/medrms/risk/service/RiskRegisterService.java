package com.zhutao.medrms.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.notification.service.NotificationService;
import com.zhutao.medrms.risk.domain.entity.RiskRegister;
import com.zhutao.medrms.risk.mapper.RiskRegisterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskRegisterService {

    private final RiskRegisterMapper riskRegisterMapper;
    // v1.44 BUG #66 修复：跨模块通知依赖
    private final NotificationService notificationService;

    public List<RiskRegister> list(String status, String category, Long projectId) {
        LambdaQueryWrapper<RiskRegister> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RiskRegister::getIsDeleted, false);
        if (status != null && !status.isBlank()) {
            wrapper.eq(RiskRegister::getStatus, status);
        }
        if (category != null && !category.isBlank()) {
            wrapper.eq(RiskRegister::getCategory, category);
        }
        // R109 G3 修复：支持按项目过滤（null = 不限）
        if (projectId != null) {
            wrapper.eq(RiskRegister::getProjectId, projectId);
        }
        wrapper.orderByDesc(RiskRegister::getCreatedAt);
        return riskRegisterMapper.selectList(wrapper);
    }

    public RiskRegister getById(Long id) {
        RiskRegister risk = riskRegisterMapper.selectById(id);
        if (risk == null || risk.getIsDeleted()) {
            throw BusinessException.notFound("RS0101", "风险登记项不存在");
        }
        return risk;
    }

    @Transactional
    public RiskRegister create(RiskRegister risk) {
        String riskNo = generateRiskNo();
        risk.setRiskNo(riskNo);
        risk.setStatus("OPEN");
        calculateRiskLevel(risk);
        riskRegisterMapper.insert(risk);

        // v1.44 BUG #66 修复：高风险创建即告警
        sendHighRiskAlertIfNeeded(risk, null);
        return risk;
    }

    @Transactional
    public RiskRegister update(Long id, RiskRegister updates) {
        RiskRegister risk = getById(id);
        String oldLevel = risk.getRiskLevel();
        if (updates.getRiskTitle() != null) risk.setRiskTitle(updates.getRiskTitle());
        if (updates.getSeverity() != null) risk.setSeverity(updates.getSeverity());
        if (updates.getProbability() != null) risk.setProbability(updates.getProbability());
        if (updates.getDetectability() != null) risk.setDetectability(updates.getDetectability());
        if (updates.getControlMeasure() != null) risk.setControlMeasure(updates.getControlMeasure());
        if (updates.getResponseStrategy() != null) risk.setResponseStrategy(updates.getResponseStrategy());
        if (updates.getStatus() != null) risk.setStatus(updates.getStatus());
        calculateRiskLevel(risk);
        riskRegisterMapper.updateById(risk);

        // v1.44 BUG #66 修复：仅当从非 HIGH 升级到 HIGH 时推送告警
        sendHighRiskAlertIfNeeded(risk, oldLevel);
        return risk;
    }

    private void sendHighRiskAlertIfNeeded(RiskRegister risk, String oldLevel) {
        try {
            if ("HIGH".equals(risk.getRiskLevel()) && !"HIGH".equals(oldLevel)
                    && risk.getOwnerId() != null) {
                notificationService.sendRiskAlertNotification(
                    risk.getOwnerId(), risk.getRiskNo(), "HIGH");
            }
        } catch (Exception e) {
            log.warn("发送风险告警通知失败: riskNo={}, err={}", risk.getRiskNo(), e.getMessage());
        }
    }

    @Transactional
    public RiskRegister close(Long id, String closureNote) {
        RiskRegister risk = getById(id);
        risk.setStatus("CLOSED");
        risk.setClosureNote(closureNote);
        risk.setClosedAt(LocalDateTime.now());
        riskRegisterMapper.updateById(risk);
        return risk;
    }

    @Transactional
    public RiskRegister acceptRisk(Long id) {
        RiskRegister risk = getById(id);
        risk.setStatus("ACCEPTED");
        riskRegisterMapper.updateById(risk);
        return risk;
    }

    private String generateRiskNo() {
        long count = riskRegisterMapper.selectCount(new LambdaQueryWrapper<RiskRegister>());
        return String.format("RSK-%06d", count + 1);
    }

    private void calculateRiskLevel(RiskRegister risk) {
        int severity = parseLevel(risk.getSeverity());
        int probability = parseLevel(risk.getProbability());
        int detectability = parseLevel(risk.getDetectability());
        int level = severity * probability * detectability;
        if (level >= 15) {
            risk.setRiskLevel("HIGH");
        } else if (level >= 8) {
            risk.setRiskLevel("MEDIUM");
        } else {
            risk.setRiskLevel("LOW");
        }
    }

    private int parseLevel(String value) {
        return switch (value) {
            case "CRITICAL", "HIGH" -> 5;
            case "MAJOR", "MEDIUM" -> 3;
            default -> 1;
        };
    }
}