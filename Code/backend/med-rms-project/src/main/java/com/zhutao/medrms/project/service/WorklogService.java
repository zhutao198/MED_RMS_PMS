package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.project.domain.entity.Worklog;
import com.zhutao.medrms.project.mapper.WorklogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * FR-2.9 工时统计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorklogService {

    private final WorklogMapper worklogMapper;

    public Worklog create(Worklog log) {
        if (log.getHours() == null || log.getHours().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("工时必须 > 0");
        }
        worklogMapper.insert(log);
        return log;
    }

    /**
     * 按项目/人员/需求 维度汇总工时
     */
    public Map<String, Object> summary(Long projectId, Long workerId, Long requirementId) {
        LambdaQueryWrapper<Worklog> w = new LambdaQueryWrapper<>();
        if (projectId != null) w.eq(Worklog::getProjectId, projectId);
        if (workerId != null) w.eq(Worklog::getWorkerId, workerId);
        if (requirementId != null) w.eq(Worklog::getRequirementId, requirementId);
        List<Worklog> all = worklogMapper.selectList(w);

        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> byWorker = new LinkedHashMap<>();
        Map<Long, BigDecimal> byTask = new LinkedHashMap<>();
        for (Worklog l : all) {
            total = total.add(l.getHours() == null ? BigDecimal.ZERO : l.getHours());
            String worker = l.getWorkerName() == null ? String.valueOf(l.getWorkerId()) : l.getWorkerName();
            byWorker.merge(worker, l.getHours(), BigDecimal::add);
            if (l.getTaskId() != null) {
                byTask.merge(l.getTaskId(), l.getHours(), BigDecimal::add);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", all.size());
        result.put("totalHours", total);
        result.put("byWorker", byWorker);
        result.put("byTask", byTask);
        return result;
    }
}
