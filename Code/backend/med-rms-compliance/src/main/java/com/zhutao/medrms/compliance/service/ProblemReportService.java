package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.compliance.domain.entity.ProblemReport;
import com.zhutao.medrms.compliance.mapper.ProblemReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemReportService {

    private final ProblemReportMapper problemReportMapper;

    @Transactional
    public ProblemReport create(ProblemReport report) {
        String reportCode = generateReportCode();
        report.setReportCode(reportCode);
        report.setStatus("Open");
        problemReportMapper.insert(report);
        log.info("创建问题报告: id={}, reportCode={}", report.getId(), reportCode);
        return report;
    }

    public IPage<ProblemReport> listByProjectId(Long projectId, int page, int size) {
        Page<ProblemReport> pageObj = new Page<>(page, size);
        return problemReportMapper.selectByProjectId(pageObj, projectId);
    }

    public IPage<ProblemReport> listBySeverity(String severity, int page, int size) {
        Page<ProblemReport> pageObj = new Page<>(page, size);
        return problemReportMapper.selectBySeverity(pageObj, severity);
    }

    public IPage<ProblemReport> listByStatus(String status, int page, int size) {
        Page<ProblemReport> pageObj = new Page<>(page, size);
        return problemReportMapper.selectByStatus(pageObj, status);
    }

    public IPage<ProblemReport> listAll(int page, int size) {
        Page<ProblemReport> pageObj = new Page<>(page, size);
        return problemReportMapper.selectAll(pageObj);
    }

    @Transactional
    public ProblemReport updateStatus(Long id, String status, String resolution) {
        ProblemReport report = problemReportMapper.selectById(id);
        if (report == null) {
            // v1.49 P2 修复：使用统一 BusinessException.notFound 替代 raw RuntimeException
            throw BusinessException.notFound("CO0401", "问题报告不存在: " + id);
        }
        report.setStatus(status);
        if (StringUtils.hasText(resolution)) {
            report.setResolution(resolution);
        }
        if ("Resolved".equals(status) || "Closed".equals(status)) {
            report.setResolvedAt(LocalDateTime.now());
        }
        problemReportMapper.updateById(report);
        log.info("更新问题报告状态: id={}, status={}", id, status);
        return report;
    }

    private String generateReportCode() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "PR-" + date + "-" + uuid;
    }
}