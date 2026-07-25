package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.project.domain.entity.Milestone;
import com.zhutao.medrms.project.domain.entity.Project;
import com.zhutao.medrms.project.domain.entity.Task;
import com.zhutao.medrms.project.mapper.MilestoneMapper;
import com.zhutao.medrms.project.mapper.ProjectMapper;
import com.zhutao.medrms.project.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GanttService {

    private final MilestoneMapper milestoneMapper;
    private final TaskMapper taskMapper;
    private final ProjectMapper projectMapper;
    private final ProjectActivityService activityService;

    public Map<String, Object> getGanttData(Long projectId) {
        Map<String, Object> data = new HashMap<>();

        // 获取里程碑
        List<Milestone> milestones = milestoneMapper.selectList(
            new LambdaQueryWrapper<Milestone>()
                .eq(Milestone::getProjectId, projectId)
                .orderByAsc(Milestone::getPlannedDate)
        );

        // 获取任务
        List<Task> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .orderByAsc(Task::getStartDate)
        );

        // 构建依赖关系
        List<Map<String, Object>> dependencies = buildDependencies(tasks);

        // 计算关键路径
        List<String> criticalPath = calculateCriticalPath(tasks);

        data.put("milestones", milestones);
        data.put("tasks", tasks);
        data.put("dependencies", dependencies);
        data.put("criticalPath", criticalPath);

        return data;
    }

    public Map<String, Object> getResourceLoad(Long projectId) {
        List<Task> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
        );

        // 按人员统计负载
        Map<Long, Map<String, Object>> resourceLoad = new HashMap<>();
        for (Task task : tasks) {
            if (task.getAssigneeId() != null) {
                if (!resourceLoad.containsKey(task.getAssigneeId())) {
                    resourceLoad.put(task.getAssigneeId(), new HashMap<>());
                    ((Map<String, Object>) resourceLoad.get(task.getAssigneeId())).put("assigneeId", task.getAssigneeId());
                    ((Map<String, Object>) resourceLoad.get(task.getAssigneeId())).put("assigneeName", task.getAssigneeName());
                    ((Map<String, Object>) resourceLoad.get(task.getAssigneeId())).put("totalHours", 0);
                }
                Integer hours = ((Map<String, Object>) resourceLoad.get(task.getAssigneeId())).get("totalHours") instanceof Integer
                    ? (Integer) ((Map<String, Object>) resourceLoad.get(task.getAssigneeId())).get("totalHours")
                    : 0;
                hours += task.getEstimatedHours() != null ? task.getEstimatedHours() : 0;
                ((Map<String, Object>) resourceLoad.get(task.getAssigneeId())).put("totalHours", hours);
            }
        }

        return Map.of("resources", resourceLoad.values());
    }

    // R227.2 DATA-013：任务编号生成改 MAX 包含软删除（R222.3 已对齐 Gantt 路径）
    @Transactional
    public Task createTask(Task task) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task> wrapper =
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>();
        wrapper.select("MAX(CAST(RIGHT(task_no, 6) AS INTEGER))")
              .likeRight("task_no", "TASK-");
        Long max = taskMapper.selectCount(wrapper);
        long next = (max == null ? 0L : max) + 1;
        task.setTaskNo(String.format("TASK-%06d", next));
        task.setStatus("TODO");
        taskMapper.insert(task);
        return task;
    }

    public Task getTaskById(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new com.zhutao.medrms.common.exception.BusinessException("PJ0101", "任务不存在");
        }
        return task;
    }

    @Transactional
    public Milestone createMilestone(Milestone milestone) {
        // R227.2 DATA-013：里程碑编号生成改 MAX 包含软删除
        if (milestone.getMilestoneNo() == null || milestone.getMilestoneNo().isBlank()) {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Milestone> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Milestone>();
            wrapper.select("MAX(CAST(RIGHT(milestone_no, 6) AS INTEGER))")
                  .likeRight("milestone_no", "MS-");
            Long max = milestoneMapper.selectCount(wrapper);
            long next = (max == null ? 0L : max) + 1;
            milestone.setMilestoneNo(String.format("MS-%06d", next));
        }
        milestone.setStatus("PLANNED");
        milestoneMapper.insert(milestone);
        return milestone;
    }

    // R225.2 CONTRACT-004：更新里程碑（前端 MilestoneList.vue "完成里程碑" 等操作）
    @Transactional
    public Milestone updateMilestone(Milestone milestone) {
        if (milestone.getId() == null) {
            throw new com.zhutao.medrms.common.exception.BusinessException("PJ0101", "里程碑 ID 不能为空");
        }
        Milestone existing = milestoneMapper.selectById(milestone.getId());
        if (existing == null) {
            throw new com.zhutao.medrms.common.exception.BusinessException("PJ0101", "里程碑不存在: id=" + milestone.getId());
        }
        // 仅更新可变字段（保护 milestoneNo / projectId 等不可变字段）
        if (milestone.getName() != null) existing.setName(milestone.getName());
        if (milestone.getDescription() != null) existing.setDescription(milestone.getDescription());
        if (milestone.getStatus() != null) existing.setStatus(milestone.getStatus());
        if (milestone.getPlannedDate() != null) existing.setPlannedDate(milestone.getPlannedDate());
        if (milestone.getActualDate() != null) existing.setActualDate(milestone.getActualDate());
        if (milestone.getGateType() != null) existing.setGateType(milestone.getGateType());
        milestoneMapper.updateById(existing);
        return existing;
    }

    public Map<String, Object> checkGate(Long milestoneId) {
        Milestone milestone = milestoneMapper.selectById(milestoneId);
        if (milestone == null) {
            return Map.of("result", "NOT_FOUND", "message", "里程碑不存在");
        }

        // 简化检查：检查该阶段门关联的需求是否都通过评审
        // 实际应检查更多条件如测试覆盖率、风险关闭率等
        boolean canPass = "COMPLETED".equals(milestone.getStatus()) ||
                         milestone.getActualDate() != null;

        return Map.of(
            "result", canPass ? "PASS" : "FAIL",
            "milestoneName", milestone.getName(),
            "gateType", milestone.getGateType(),
            "message", canPass ? "阶段门检查通过" : "阶段门检查未通过"
        );
    }

    private List<Map<String, Object>> buildDependencies(List<Task> tasks) {
        List<Map<String, Object>> deps = new java.util.ArrayList<>();
        for (Task task : tasks) {
            if (task.getParentTaskId() != null) {
                deps.add(Map.of(
                    "from", task.getParentTaskId(),
                    "to", task.getId(),
                    "type", "FS" // Finish-to-Start
                ));
            }
        }
        return deps;
    }

    private List<String> calculateCriticalPath(List<Task> tasks) {
        // 简化实现：返回所有任务作为关键路径
        return tasks.stream().map(Task::getTaskNo).toList();
    }

    // ===== R175 FR-2.7: 甘特图拖拽调整任务时间 =====
    @Transactional
    public Task adjustTaskDates(Long taskId, LocalDate newStart, LocalDate newEnd, Long operatorId, String operatorName) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new com.zhutao.medrms.common.exception.BusinessException("PJ0101", "任务不存在");
        }
        String oldSummary = task.getTitle() + " [" + task.getStartDate() + " → " + task.getEndDate() + "]";
        if (newStart != null) task.setStartDate(newStart);
        if (newEnd != null) task.setEndDate(newEnd);
        taskMapper.updateById(task);

        // 记录活动流
        activityService.recordActivity(task.getProjectId(), "GANTT_CHANGED",
            operatorName + " 调整了任务 '" + task.getTitle() + "' 的日期: " + oldSummary
                + " → [" + task.getStartDate() + " → " + task.getEndDate() + "]",
            null, operatorId, operatorName, "TASK", taskId);
        return task;
    }

    // ===== R175 FR-2.8: 跨项目资源调整建议 =====
    public List<Map<String, Object>> suggestAdjustments(Long assigneeId) {
        // 找到该人员所有待办任务，按优先级排序，推荐可推迟的低优先级、非关键路径任务
        List<Task> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getAssigneeId, assigneeId)
                .in(Task::getStatus, "TODO", "IN_PROGRESS"));
        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (Task t : tasks) {
            if ("LOW".equals(t.getPriority()) || "TODO".equals(t.getStatus())) {
                Map<String, Object> s = new HashMap<>();
                s.put("taskId", t.getId());
                s.put("taskTitle", t.getTitle());
                s.put("projectId", t.getProjectId());
                s.put("priority", t.getPriority());
                s.put("status", t.getStatus());
                s.put("estimatedHours", t.getEstimatedHours());
                s.put("suggestion", "该任务优先级较低，建议推迟或转分配给其他人");
                suggestions.add(s);
            }
        }
        return suggestions;
    }
}