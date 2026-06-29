package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.util.SecurityUtils;
import com.zhutao.medrms.compliance.mapper.ReportMapper;
import com.zhutao.medrms.compliance.domain.entity.Report;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Set<String> ALLOWED_TYPES = Set.of("TRACEABILITY", "CHANGE", "COMPLIANCE", "RISK");

    private final ReportMapper reportMapper;

    public List<Map<String, Object>> getReports(Long projectId, String reportType) {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Report::getDeleted, false);
        if (projectId != null) {
            wrapper.eq(Report::getProjectId, projectId);
        }
        if (reportType != null && !reportType.isBlank()) {
            wrapper.eq(Report::getReportType, reportType);
        }
        wrapper.orderByDesc(Report::getGeneratedAt);

        log.info("Querying reports with projectId={}, reportType={}", projectId, reportType);
        List<Report> reports = reportMapper.selectList(wrapper);
        log.info("Found {} reports in database", reports.size());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Report r : reports) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("reportType", r.getReportType());
            map.put("title", r.getTitle());
            map.put("projectId", r.getProjectId());
            map.put("generatedAt", r.getGeneratedAt() != null ? r.getGeneratedAt().toString() : LocalDateTime.now().toString());
            result.add(map);
        }

        return result;
    }

    public Map<String, Object> generateReport(String reportType, Long projectId) {
        if (reportType == null || reportType.isBlank()) {
            throw BusinessException.param("reportType 不能为空");
        }
        if (!ALLOWED_TYPES.contains(reportType)) {
            throw BusinessException.param("reportType 非法，可选：" + ALLOWED_TYPES);
        }
        if (projectId == null) {
            throw BusinessException.param("projectId 不能为空");
        }

        Report report = new Report();
        report.setReportType(reportType);
        report.setProjectId(projectId);
        report.setTitle(reportType + " Report - Project " + projectId);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        report.setGeneratedBy(currentUserId != null ? currentUserId : 1L);
        report.setGeneratedAt(LocalDateTime.now());
        report.setDeleted(false);

        reportMapper.insert(report);

        Map<String, Object> result = new HashMap<>();
        result.put("id", report.getId());
        result.put("reportType", reportType);
        result.put("title", report.getTitle());
        result.put("projectId", projectId);
        result.put("generatedAt", report.getGeneratedAt().toString());

        log.info("Generated report: type={}, projectId={}, by={}", reportType, projectId, report.getGeneratedBy());
        return result;
    }

    public byte[] downloadReport(Long id) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            // v1.41 BUG #42 修复：不存在记录抛 404 而非 200 文本
            throw BusinessException.notFound("RP0101", "报表不存在: " + id);
        }

        // v1.41 BUG #41 修复：统一 UTF-8 编码，避免 Windows GBK 默认编码导致中文乱码
        StringBuilder content = new StringBuilder();
        content.append("===========================================\n");
        content.append("           Med-RMS 报表\n");
        content.append("===========================================\n");
        content.append("报表类型: ").append(report.getReportType()).append("\n");
        content.append("标题: ").append(report.getTitle()).append("\n");
        content.append("项目ID: ").append(report.getProjectId()).append("\n");
        content.append("生成时间: ").append(report.getGeneratedAt()).append("\n");
        content.append("===========================================\n");
        content.append("报表内容...\n");

        log.info("Downloading report: id={}", id);
        return content.toString().getBytes(StandardCharsets.UTF_8);
    }
}