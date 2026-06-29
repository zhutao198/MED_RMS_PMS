package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.compliance.domain.entity.ReportConfig;
import com.zhutao.medrms.compliance.mapper.ReportConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报表配置服务（v1.46 P1-后端-1）
 * 替代 ReportsCustom.vue 之前用 /reports/generate 折中保存的方案。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportConfigService {

    private final ReportConfigMapper reportConfigMapper;

    public List<ReportConfig> listByCreator(Long userId, String reportType, Long projectId) {
        LambdaQueryWrapper<ReportConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportConfig::getIsDeleted, false);
        if (userId != null) {
            wrapper.and(w -> w.eq(ReportConfig::getCreatedBy, userId)
                    .or().eq(ReportConfig::getIsShared, true));
        }
        if (reportType != null && !reportType.isBlank()) {
            wrapper.eq(ReportConfig::getReportType, reportType);
        }
        if (projectId != null) {
            wrapper.eq(ReportConfig::getProjectId, projectId);
        }
        wrapper.orderByDesc(ReportConfig::getUpdatedAt);
        return reportConfigMapper.selectList(wrapper);
    }

    public ReportConfig getById(Long id) {
        ReportConfig cfg = reportConfigMapper.selectById(id);
        if (cfg == null || cfg.getIsDeleted()) {
            throw BusinessException.notFound("RC0101", "报表配置不存在");
        }
        return cfg;
    }

    @Transactional
    public ReportConfig create(ReportConfig config) {
        if (config.getName() == null || config.getName().isBlank()) {
            throw BusinessException.param("name 不能为空");
        }
        if (config.getReportType() == null || config.getReportType().isBlank()) {
            throw BusinessException.param("reportType 不能为空");
        }
        if (config.getFieldsJson() == null || config.getFieldsJson().isBlank()) {
            throw BusinessException.param("fieldsJson 不能为空");
        }
        // 同名唯一性
        LambdaQueryWrapper<ReportConfig> check = new LambdaQueryWrapper<>();
        check.eq(ReportConfig::getName, config.getName())
                .eq(ReportConfig::getCreatedBy, config.getCreatedBy())
                .eq(ReportConfig::getIsDeleted, false);
        if (reportConfigMapper.selectCount(check) > 0) {
            throw BusinessException.param("同用户下已存在同名报表配置：" + config.getName());
        }
        if (config.getIsShared() == null) config.setIsShared(false);
        config.setIsDeleted(false);
        reportConfigMapper.insert(config);
        log.info("创建报表配置: id={}, name={}, type={}", config.getId(), config.getName(), config.getReportType());
        return config;
    }

    @Transactional
    public ReportConfig update(Long id, ReportConfig updates) {
        ReportConfig cfg = getById(id);
        if (updates.getName() != null) cfg.setName(updates.getName());
        if (updates.getDescription() != null) cfg.setDescription(updates.getDescription());
        if (updates.getReportType() != null) cfg.setReportType(updates.getReportType());
        if (updates.getProjectId() != null) cfg.setProjectId(updates.getProjectId());
        if (updates.getFieldsJson() != null) cfg.setFieldsJson(updates.getFieldsJson());
        if (updates.getFiltersJson() != null) cfg.setFiltersJson(updates.getFiltersJson());
        if (updates.getIsShared() != null) cfg.setIsShared(updates.getIsShared());
        cfg.setUpdatedAt(LocalDateTime.now());
        reportConfigMapper.updateById(cfg);
        log.info("更新报表配置: id={}", id);
        return cfg;
    }

    @Transactional
    public void delete(Long id) {
        ReportConfig cfg = getById(id);
        cfg.setIsDeleted(true);
        cfg.setUpdatedAt(LocalDateTime.now());
        reportConfigMapper.updateById(cfg);
        log.info("删除报表配置（软删）: id={}", id);
    }
}
