package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.compliance.domain.entity.SoupComponent;
import com.zhutao.medrms.compliance.mapper.SoupComponentMapper;
import com.zhutao.medrms.risk.domain.entity.RiskAssessment;
import com.zhutao.medrms.risk.service.RiskAssessmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SoupComponentService {

    private final SoupComponentMapper soupComponentMapper;
    private final RiskAssessmentService riskAssessmentService;

    public List<SoupComponent> list(String status, String riskLevel) {
        LambdaQueryWrapper<SoupComponent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SoupComponent::getIsDeleted, false);
        if (status != null && !status.isBlank()) {
            wrapper.eq(SoupComponent::getStatus, status);
        }
        if (riskLevel != null && !riskLevel.isBlank()) {
            wrapper.eq(SoupComponent::getRiskLevel, riskLevel);
        }
        return soupComponentMapper.selectList(wrapper);
    }

    public SoupComponent getById(Long id) {
        SoupComponent component = soupComponentMapper.selectById(id);
        if (component == null || component.getIsDeleted()) {
            throw BusinessException.notFound("SP0101", "SOUP组件不存在");
        }
        return component;
    }

    @Transactional
    public SoupComponent create(SoupComponent component) {
        component.setStatus("ACTIVE");
        component.setCreatedAt(LocalDateTime.now());
        soupComponentMapper.insert(component);
        log.info("创建SOUP组件: name={}, code={}", component.getComponentName(), component.getComponentCode());
        return component;
    }

    @Transactional
    public SoupComponent update(Long id, SoupComponent updates) {
        SoupComponent component = getById(id);
        if (updates.getComponentName() != null) component.setComponentName(updates.getComponentName());
        if (updates.getVersion() != null) component.setVersion(updates.getVersion());
        if (updates.getSupplier() != null) component.setSupplier(updates.getSupplier());
        if (updates.getRiskLevel() != null) component.setRiskLevel(updates.getRiskLevel());
        if (updates.getStatus() != null) component.setStatus(updates.getStatus());
        if (updates.getCertificationDoc() != null) component.setCertificationDoc(updates.getCertificationDoc());
        if (updates.getLicenseExpiry() != null) component.setLicenseExpiry(updates.getLicenseExpiry());
        if (updates.getSecurityDisclosure() != null) component.setSecurityDisclosure(updates.getSecurityDisclosure());
        if (updates.getLastSecurityUpdate() != null) component.setLastSecurityUpdate(updates.getLastSecurityUpdate());
        soupComponentMapper.updateById(component);
        log.info("更新SOUP组件: id={}", id);
        return component;
    }

    @Transactional
    public void delete(Long id) {
        SoupComponent component = getById(id);
        component.setIsDeleted(true);
        soupComponentMapper.updateById(component);
        log.info("删除SOUP组件: id={}", id);
    }

    @Transactional
    public SoupComponent renewLicense(Long id) {
        SoupComponent component = getById(id);
        component.setLicenseExpiry(LocalDateTime.now().plusYears(1));
        soupComponentMapper.updateById(component);
        log.info("续期SOUP组件许可证: id={}", id);
        return component;
    }

    public List<Map<String, Object>> getAnomalies(Long componentId) {
        return collectAnomalies(getById(componentId));
    }

    /**
     * 批量检测项目下所有 SOUP 组件的异常（FR-1.11）
     * 返回结构：[{componentId, componentName, version, projectId, anomalies: [...]}]
     *
     * v1.45 BUG #92 修复：SoupComponent.projectId 是 @TableField(exist=false) 非 DB 字段，
     * 不能作为 MP lambda 表达式过滤条件，否则抛 MybatisPlusException。
     * 改为：先全量查未删除的 SOUP 组件，再在内存中按 projectId 过滤（null 表示全部）。
     */
    public List<Map<String, Object>> getAllAnomalies(Long projectId) {
        LambdaQueryWrapper<SoupComponent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SoupComponent::getIsDeleted, false);
        List<SoupComponent> components = soupComponentMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (SoupComponent c : components) {
            // projectId 是非 DB 字段（@TableField(exist=false)），必须内存过滤
            if (projectId != null && c.getProjectId() != null && !projectId.equals(c.getProjectId())) {
                continue;
            }
            List<Map<String, Object>> anomalies = collectAnomalies(c);
            if (!anomalies.isEmpty()) {
                Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("componentId", c.getId());
                item.put("componentName", c.getComponentName());
                item.put("version", c.getVersion());
                item.put("projectId", c.getProjectId());
                item.put("anomalies", anomalies);
                result.add(item);
            }
        }
        log.info("SOUP 批量异常检测: projectId={}, 命中组件={}", projectId, result.size());
        return result;
    }

    private List<Map<String, Object>> collectAnomalies(SoupComponent component) {
        List<Map<String, Object>> anomalies = new ArrayList<>();
        if (component.getLicenseExpiry() != null && component.getLicenseExpiry().isBefore(LocalDateTime.now())) {
            anomalies.add(buildAnomaly("LICENSE_EXPIRED", "组件 [" + component.getComponentName() + "] 许可证已过期",
                    "HIGH", "licenseExpiry", component.getLicenseExpiry().toString()));
        }
        if (component.getLicenseExpiry() != null && component.getLicenseExpiry().isBefore(LocalDateTime.now().plusMonths(3))) {
            anomalies.add(buildAnomaly("LICENSE_EXPIRING", "组件 [" + component.getComponentName() + "] 许可证将在3个月内过期",
                    "MEDIUM", "licenseExpiry", component.getLicenseExpiry().toString()));
        }
        if ("HIGH".equals(component.getRiskLevel()) && component.getLastSecurityUpdate() != null
                && component.getLastSecurityUpdate().isBefore(LocalDateTime.now().minusMonths(6))) {
            anomalies.add(buildAnomaly("SECURITY_OUTDATED", "高风险组件 [" + component.getComponentName() + "] 超过6个月未更新安全补丁",
                    "HIGH", "lastSecurityUpdate", component.getLastSecurityUpdate().toString()));
        }
        if ("ACTIVE".equals(component.getStatus()) && component.getCertificationDoc() == null) {
            anomalies.add(buildAnomaly("MISSING_CERTIFICATION", "组件 [" + component.getComponentName() + "] 缺少认证文档",
                    "HIGH", "certificationDoc", null));
        }
        return anomalies;
    }

    private Map<String, Object> buildAnomaly(String type, String message, String severity, String field, Object value) {
        Map<String, Object> a = new java.util.LinkedHashMap<>();
        a.put("type", type);
        a.put("message", message);
        a.put("severity", severity);
        a.put("field", field);
        a.put("value", value);
        return a;
    }

    /**
     * 将 SOUP 组件的异常自动关联为风险评估（FR-1.11 SOUP 风险自动关联）
     * - 调用方传入 requirementId（由前端从项目 URS 列表选择）
     * - 每个 HIGH/MEDIUM 异常创建一条 RiskAssessment
     * - 异常消息作为 hazardSource/hazardSituation
     */
    @Transactional
    public List<RiskAssessment> linkAnomaliesToRisk(Long componentId, Long requirementId, Long assessedBy) {
        SoupComponent component = getById(componentId);
        List<Map<String, Object>> anomalies = collectAnomalies(component);
        if (anomalies.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<RiskAssessment> created = new ArrayList<>();
        for (Map<String, Object> a : anomalies) {
            String severity = String.valueOf(a.get("severity"));
            String riskLevel = "HIGH".equals(severity) ? "HIGH" : ("MEDIUM".equals(severity) ? "MEDIUM" : "LOW");
            String hazardLevel = "HIGH".equals(severity) ? "SERIOUS" : "MINOR";
            RiskAssessment r = riskAssessmentService.assess(
                    requirementId,
                    riskLevel,
                    hazardLevel,
                    "SOUP 组件 [" + component.getComponentName() + " v" + component.getVersion() + "] - " + a.get("type"),
                    String.valueOf(a.get("message")),
                    "SOUP 异常可能导致 " + a.get("type"),
                    "按 SOUP 管理流程修复：" + a.get("type"),
                    assessedBy
            );
            created.add(r);
        }
        log.info("SOUP {} 异常→风险评估: 创建 {} 条", componentId, created.size());
        return created;
    }
}