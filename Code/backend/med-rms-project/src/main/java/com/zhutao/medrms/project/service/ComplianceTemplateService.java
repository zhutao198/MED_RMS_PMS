package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.project.domain.entity.ComplianceTemplate;
import com.zhutao.medrms.project.domain.entity.Project;
import com.zhutao.medrms.project.mapper.ComplianceTemplateMapper;
import com.zhutao.medrms.project.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 合规模板服务 - FR-1.9
 * 负责模板管理、应用模板到项目、自定义模板保存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceTemplateService {

    private final ComplianceTemplateMapper templateMapper;
    private final ProjectMapper projectMapper;

    /**
     * 列出所有可用模板（含 4 预设 + 自定义）
     */
    public List<ComplianceTemplate> listAll() {
        return templateMapper.selectAllActive();
    }

    /**
     * 根据 ID 查找
     */
    public ComplianceTemplate getById(Long id) {
        ComplianceTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw BusinessException.notFound("TPL0101", "合规模板不存在: id=" + id);
        }
        return t;
    }

    /**
     * 根据 code 查找
     */
    public ComplianceTemplate getByCode(String code) {
        return templateMapper.findByCode(code);
    }

    /**
     * 创建自定义模板
     */
    @Transactional
    public ComplianceTemplate createCustom(ComplianceTemplate template, Long userId, String userName) {
        if (templateMapper.findByCode(template.getCode()) != null) {
            throw BusinessException.stateConflict("模板编号已存在: " + template.getCode());
        }
        template.setType("CUSTOM");
        template.setCreatedBy(userId);
        template.setCreatedByName(userName);
        template.setIsActive(true);
        if (template.getId() != null) template.setId(null);
        templateMapper.insert(template);
        log.info("创建自定义合规模板: code={}, name={}", template.getCode(), template.getName());
        return template;
    }

    /**
     * 更新自定义模板
     */
    @Transactional
    public ComplianceTemplate updateCustom(Long id, ComplianceTemplate template) {
        ComplianceTemplate existing = templateMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.notFound("TPL0101", "模板不存在: id=" + id);
        }
        if ("PRESET".equals(existing.getType())) {
            throw BusinessException.stateConflict("系统预设模板不可修改");
        }
        existing.setName(template.getName());
        existing.setDescription(template.getDescription());
        existing.setConfigJson(template.getConfigJson());
        templateMapper.updateById(existing);
        log.info("更新自定义模板: id={}, code={}", id, existing.getCode());
        return existing;
    }

    /**
     * 删除自定义模板（软删除）
     */
    @Transactional
    public void deleteCustom(Long id) {
        ComplianceTemplate existing = templateMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.notFound("TPL0101", "模板不存在: id=" + id);
        }
        if ("PRESET".equals(existing.getType())) {
            throw BusinessException.stateConflict("系统预设模板不可删除");
        }
        existing.setIsActive(false);
        templateMapper.updateById(existing);
        log.info("删除自定义模板: id={}, code={}", id, existing.getCode());
    }

    /**
     * 应用模板到项目：将项目关联到模板，并把模板配置中的预填字段写入项目描述
     * 这是 FR-1.9 的核心"一键创建合规项目"流程
     */
    @Transactional
    public Project applyTemplateToProject(Long projectId, Long templateId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw BusinessException.notFound("PRJ0101", "项目不存在: id=" + projectId);
        }
        ComplianceTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw BusinessException.notFound("TPL0101", "合规模板不存在: id=" + templateId);
        }
        project.setTemplateId(template.getId());
        project.setTemplateCode(template.getCode());
        // 把模板的"URS 预填字段"附加到项目描述前缀
        String prefix = "【" + template.getName() + "】";
        if (project.getDescription() == null || !project.getDescription().startsWith("【")) {
            project.setDescription(prefix + (project.getDescription() == null ? "" : " " + project.getDescription()));
        }
        projectMapper.updateById(project);
        log.info("应用模板到项目: projectId={}, templateId={}, code={}", projectId, templateId, template.getCode());
        return project;
    }

    /**
     * 启动时初始化 4 个预设模板（FR-1.9）
     */
    @Transactional
    public void initPresetTemplates() {
        ensurePreset("NMPA", "NMPA注册项目模板", Map.of(
                "defaultUrsFields", Map.of("regulatoryTarget", "NMPA", "safetyClass", "依据GB 9706.1判定"),
                "reviewProcess", List.of("pre-review", "formal-review", "final-review"),
                "dcpGates", List.of("PLANNING", "REQUIREMENTS", "DESIGN", "VERIFICATION", "RELEASE"),
                "evidencePackage", List.of("URS", "SRS", "SDS", "V&V", "风险档案", "DHF"),
                "regulationRefs", List.of("医疗器械注册管理办法", "GB 9706.1-2020", "YY/T 0316")
        ));
        ensurePreset("ISO13485", "ISO 13485合规项目模板", Map.of(
                "defaultUrsFields", Map.of("regulatoryTarget", "ISO13485", "qualitySystem", "ISO 13485:2016"),
                "reviewProcess", List.of("management-review", "technical-review", "audit"),
                "dcpGates", List.of("PLANNING", "REQUIREMENTS", "DESIGN", "VERIFICATION", "RELEASE"),
                "evidencePackage", List.of("质量手册", "程序文件", "作业指导", "记录"),
                "regulationRefs", List.of("ISO 13485:2016", "ISO 9001:2015")
        ));
        ensurePreset("IEC62304", "IEC 62304软件项目模板", Map.of(
                "defaultUrsFields", Map.of("regulatoryTarget", "IEC62304", "safetyClass", "A/B/C"),
                "reviewProcess", List.of("pre-review", "formal-review", "final-review"),
                "dcpGates", List.of("PLANNING", "REQUIREMENTS", "DESIGN", "VERIFICATION", "RELEASE"),
                "evidencePackage", List.of("SDP", "SRS", "SDS", "V&V", "SOUP清单", "风险档案"),
                "regulationRefs", List.of("IEC 62304:2006/Amd1:2015", "ISO 14971:2019")
        ));
        ensurePreset("FDA510K", "FDA 510(k)项目模板", Map.of(
                "defaultUrsFields", Map.of("regulatoryTarget", "FDA", "submissionType", "510(k)", "language", "EN"),
                "reviewProcess", List.of("pre-review", "formal-review", "FDA-pre-submission", "final-review"),
                "dcpGates", List.of("PLANNING", "REQUIREMENTS", "DESIGN", "VERIFICATION", "RELEASE", "FDA_SUBMISSION"),
                "evidencePackage", List.of("URS-EN", "SRS-EN", "SDS-EN", "V&V", "Risk-EN", "DHF", "Predicate Comparison"),
                "regulationRefs", List.of("21 CFR Part 820", "21 CFR Part 11", "FDA 510(k) Guidance")
        ));
        log.info("FR-1.9 4 个预设合规模板已就绪");
    }

    private void ensurePreset(String code, String name, Map<String, Object> config) {
        if (templateMapper.findByCode(code) == null) {
            ComplianceTemplate t = new ComplianceTemplate();
            t.setCode(code);
            t.setName(name);
            t.setType("PRESET");
            t.setDescription("系统预设模板：" + name);
            t.setConfigJson(toJson(config));
            t.setIsActive(true);
            templateMapper.insert(t);
            log.info("初始化预设模板: {}", code);
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            log.warn("模板配置转 JSON 失败", e);
            return "{}";
        }
    }
}
