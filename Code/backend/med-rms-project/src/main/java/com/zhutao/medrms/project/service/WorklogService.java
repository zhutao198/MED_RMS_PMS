package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.project.domain.entity.Project;
import com.zhutao.medrms.project.domain.entity.Task;
import com.zhutao.medrms.project.domain.entity.Worklog;
import com.zhutao.medrms.project.mapper.ProjectMapper;
import com.zhutao.medrms.project.mapper.TaskMapper;
import com.zhutao.medrms.project.mapper.WorklogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FR-2.9 工时统计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorklogService {

    private final WorklogMapper worklogMapper;
    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ProjectActivityService activityService;

    public Worklog create(Worklog log) {
        if (log.getHours() == null || log.getHours().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("工时必须 > 0");
        }
        worklogMapper.insert(log);
        checkBudgetExceeded(log.getProjectId());
        return log;
    }

    /** R175 G5: 工时超预算 120% 自动通知 */
    private void checkBudgetExceeded(Long projectId) {
        if (projectId == null) return;
        try {
            Project project = projectMapper.selectById(projectId);
            if (project == null || project.getBudgetAlarmPct() == null) return;
            int alarmPct = project.getBudgetAlarmPct();

            // 获取总预估工时（预算）
            Integer totalBudget = taskMapper.selectList(
                new LambdaQueryWrapper<Task>().eq(Task::getProjectId, projectId))
                .stream().mapToInt(t -> t.getEstimatedHours() != null ? t.getEstimatedHours() : 0).sum();
            if (totalBudget <= 0) return;

            // 获取已填报总工时
            Double totalActual = worklogMapper.selectList(
                new LambdaQueryWrapper<Worklog>().eq(Worklog::getProjectId, projectId))
                .stream().mapToDouble(w -> w.getHours() != null ? w.getHours().doubleValue() : 0).sum();

            double threshold = totalBudget * alarmPct / 100.0;
            if (totalActual > threshold) {
                activityService.recordActivity(projectId, "PROJECT_CONFIG_CHANGED",
                    "项目总工时 " + totalActual + "h 已超过预算 " + totalBudget + "h 的 " + alarmPct + "% 阈值",
                    null, null, null, "PROJECT", projectId);
                log.warn("工时超预算: projectId={}, actual={}, budget={}, threshold={}",
                    projectId, totalActual, totalBudget, threshold);
            }
        } catch (Exception e) {
            log.debug("预算检查跳过: {}", e.getMessage());
        }
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
