package com.zhutao.medrms.compliance.service;

import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.compliance.domain.entity.ReportTemplate;
import com.zhutao.medrms.compliance.mapper.ReportTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 报表模板服务
 * 详细设计: 支撑域与通用域-详细设计.md §3.2 ReportTemplate
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportTemplateService {

    private final ReportTemplateMapper reportTemplateMapper;

    public List<ReportTemplate> listActive() {
        return reportTemplateMapper.selectActive();
    }

    public ReportTemplate getByType(String type) {
        if (type == null || type.isBlank()) {
            throw BusinessException.param("type 不能为空");
        }
        ReportTemplate tpl = reportTemplateMapper.selectByType(type);
        if (tpl == null) {
            throw BusinessException.notFound("RT0404", "未找到报表模板 type=" + type);
        }
        return tpl;
    }
}
