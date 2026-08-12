package com.zhutao.medrms.project.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.annotation.AuditLog;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.project.domain.entity.Milestone;
import com.zhutao.medrms.project.domain.entity.Project;
import com.zhutao.medrms.project.domain.entity.Task;
import com.zhutao.medrms.project.mapper.MilestoneMapper;
import com.zhutao.medrms.project.mapper.ProjectMapper;
import com.zhutao.medrms.project.mapper.TaskMapper;
import com.zhutao.medrms.project.service.GanttService;
import com.zhutao.medrms.project.service.TaskPredecessorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "甘特图与里程碑", description = "甘特图、里程碑、任务管理接口")
@RestController
@RequestMapping("/gantt")
@RequiredArgsConstructor
public class GanttController {

    private final GanttService ganttService;
    private final TaskPredecessorService predecessorService;
    private final MilestoneMapper milestoneMapper;
    private final TaskMapper taskMapper;
    private final ProjectMapper projectMapper;

    @Operation(summary = "获取甘特图数据")
    @GetMapping("/project/{projectId}")
    public Result<Map<String, Object>> getGanttData(@PathVariable Long projectId) {
        return Result.success(ganttService.getGanttData(projectId));
    }

    @Operation(summary = "获取资源负载")
    @GetMapping("/resources/{projectId}")
    public Result<Map<String, Object>> getResourceLoad(@PathVariable Long projectId) {
        return Result.success(ganttService.getResourceLoad(projectId));
    }

    @Operation(summary = "创建任务")
    @PostMapping("/tasks")
    public Result<Task> createTask(@RequestBody Task task) {
        return Result.success(ganttService.createTask(task));
    }

    @Operation(summary = "更新任务（FR-2.7 拖拽后调整日期）")
    @AuditLog(eventType = "MODIFY", entityType = "TASK", operation = "更新任务", entityIdSpel = "#id")
    @PutMapping("/tasks/{id}")
    public Result<Task> updateTask(@PathVariable Long id, @RequestBody Task updates,
                                   @RequestParam(required = false) Long operatorId,
                                   @RequestParam(required = false) String operatorName) {
        Task task = ganttService.getTaskById(id);
        if (updates.getTitle() != null) task.setTitle(updates.getTitle());
        if (updates.getStartDate() != null) task.setStartDate(updates.getStartDate());
        if (updates.getEndDate() != null) task.setEndDate(updates.getEndDate());
        if (updates.getStatus() != null) task.setStatus(updates.getStatus());
        // R222 C2: 清空负责人约定 — 前端传 -1L 表示清空（assigneeName 一并清空）
        if (updates.getAssigneeId() != null) {
            if (updates.getAssigneeId() == -1L) {
                task.setAssigneeId(null);
                task.setAssigneeName(null);
            } else {
                task.setAssigneeId(updates.getAssigneeId());
                task.setAssigneeName(updates.getAssigneeName());
            }
        } else if (updates.getAssigneeName() != null) {
            // 仅传 name（不传 id）— 视为保持 assigneeId 不变，仅更新冗余缓存
            task.setAssigneeName(updates.getAssigneeName());
        }
        if (updates.getEstimatedHours() != null) task.setEstimatedHours(updates.getEstimatedHours());
        if (updates.getPriority() != null) task.setPriority(updates.getPriority());
        taskMapper.updateById(task);
        return Result.success(task);
    }

    @Operation(summary = "创建里程碑")
    @PostMapping("/milestones")
    public Result<Milestone> createMilestone(@RequestBody Milestone milestone) {
        return Result.success(ganttService.createMilestone(milestone));
    }

    // R225.2 CONTRACT-004：更新里程碑（前端 MilestoneList.vue "完成里程碑" 调用）
    @Operation(summary = "更新里程碑")
    @AuditLog(eventType = "MODIFY", entityType = "MILESTONE", operation = "更新里程碑", entityIdSpel = "#id")
    @PutMapping("/milestones/{id}")
    public Result<Milestone> updateMilestone(@PathVariable Long id, @RequestBody Milestone milestone) {
        milestone.setId(id);
        return Result.success(ganttService.updateMilestone(milestone));
    }

    @Operation(summary = "阶段门检查")
    @GetMapping("/gate/{milestoneId}/check")
    public Result<Map<String, Object>> checkGate(@PathVariable Long milestoneId) {
        return Result.success(ganttService.checkGate(milestoneId));
    }

    @Operation(summary = "获取项目里程碑")
    @GetMapping("/milestones/project/{projectId}")
    public Result<List<Milestone>> getMilestones(@PathVariable Long projectId) {
        List<Milestone> milestones = milestoneMapper.selectList(
            new LambdaQueryWrapper<Milestone>()
                .eq(Milestone::getProjectId, projectId)
                .orderByAsc(Milestone::getPlannedDate)
        );
        return Result.success(milestones);
    }

    @Operation(summary = "获取项目任务")
    @GetMapping("/tasks/project/{projectId}")
    public Result<List<Task>> getTasks(@PathVariable Long projectId) {
        List<Task> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .orderByAsc(Task::getStartDate)
        );
        return Result.success(tasks);
    }

    // ===== v1.43 P1-3 修复：任务前置依赖持久化 =====

    @Operation(summary = "查询某任务的前置任务 ID 列表")
    @GetMapping("/tasks/{id}/predecessors")
    public Result<List<Long>> listPredecessors(@PathVariable Long id) {
        return Result.success(predecessorService.listPredecessorIds(id));
    }

    @Operation(summary = "覆盖更新某任务的前置依赖（FR-2.7 关键路径用）")
    @PutMapping("/tasks/{id}/predecessors")
    public Result<List<Long>> updatePredecessors(@PathVariable Long id, @RequestBody List<Long> predecessorIds) {
        return Result.success(predecessorService.updatePredecessors(id, predecessorIds));
    }

    @Operation(summary = "加载项目任务依赖图（taskId -> [predecessorIds]）")
    @GetMapping("/dependencies/project/{projectId}")
    public Result<java.util.Map<Long, List<Long>>> loadProjectGraph(@PathVariable Long projectId) {
        return Result.success(predecessorService.loadProjectGraph(projectId));
    }

    @Operation(summary = "获取项目燃尽图数据（FR-1.2 仪表盘组件）")
    @GetMapping("/burndown/{projectId}")
    public Result<Map<String, Object>> getBurndown(@PathVariable Long projectId) {
        Project project = projectMapper.selectById(projectId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (project == null) {
            result.put("dates", List.of());
            result.put("ideal", List.of());
            result.put("actual", List.of());
            result.put("reason", "PROJECT_NOT_FOUND");
            return Result.success(result);
        }
        // D-15 修复：项目无 startDate 时返回明确原因，前端可显示"请先设置项目起止日期"
        if (project.getStartDate() == null) {
            result.put("dates", List.of());
            result.put("ideal", List.of());
            result.put("actual", List.of());
            result.put("reason", "NO_START_DATE");
            return Result.success(result);
        }

        LocalDate start = project.getStartDate();
        LocalDate end = project.getEndDate() != null ? project.getEndDate() : start.plusDays(30);
        if (end.isBefore(start)) end = start.plusDays(30);
        long totalDays = ChronoUnit.DAYS.between(start, end);
        if (totalDays <= 0) totalDays = 1;

        List<Task> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId));
        long totalEffort = tasks.stream()
            .mapToLong(t -> t.getEstimatedHours() != null ? t.getEstimatedHours() : 0)
            .sum();
        long doneEffort = tasks.stream()
            .filter(t -> "DONE".equals(t.getStatus()))
            .mapToLong(t -> t.getEstimatedHours() != null ? t.getEstimatedHours() : 0)
            .sum();
        // D-15 修复：无 estimatedHours 时也返回基本信息（不空），让前端知道项目存在但缺数据
        if (totalEffort == 0) {
            result.put("dates", List.of(start.toString(), end.toString()));
            result.put("ideal", List.of(0, 0));
            result.put("actual", List.of(0, 0));
            result.put("reason", "NO_ESTIMATED_HOURS");
            result.put("totalEffort", 0);
            result.put("doneEffort", 0);
            return Result.success(result);
        }

        double dailyRate = (double) totalEffort / totalDays;
        List<String> dates = new ArrayList<>();
        List<Integer> ideal = new ArrayList<>();
        List<Integer> actual = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // D-19 修复：原实现在 d <= today 时用 totalEffort * (d-start)/totalDays 作为累计完成量，
        // 这在数学上等于理想线（线性插值），导致 ideal 与 actual 完全重合。
        // 修复：使用每个任务的实际完成日期（updatedAt）作为完成时间，累加计算真实剩余工作量
        // 优先按 updatedAt 累加；updatedAt 为空时按 createdAt 兜底
        Map<LocalDate, Long> completedByDate = new java.util.TreeMap<>();
        long completedBeforeStart = 0;
        for (Task t : tasks) {
            if (!"DONE".equals(t.getStatus())) continue;
            long effort = t.getEstimatedHours() != null ? t.getEstimatedHours() : 0;
            java.time.LocalDateTime completionTime = t.getUpdatedAt() != null ? t.getUpdatedAt() : t.getCreatedAt();
            if (completionTime == null) {
                completedBeforeStart += effort;
                continue;
            }
            LocalDate completionDate = completionTime.toLocalDate();
            completedByDate.merge(completionDate, effort, Long::sum);
        }
        // 转为累计和：completionDay → 当天结束时累计完成的工时
        long cumulativeCompleted = completedBeforeStart;
        Map<LocalDate, Long> cumulativeByDate = new java.util.TreeMap<>();
        for (Map.Entry<LocalDate, Long> e : completedByDate.entrySet()) {
            cumulativeCompleted += e.getValue();
            cumulativeByDate.put(e.getKey(), cumulativeCompleted);
        }

        for (long i = 0; i <= totalDays; i++) {
            LocalDate d = start.plusDays(i);
            dates.add(d.toString());
            int idealRemaining = (int) Math.round(totalEffort - dailyRate * i);
            ideal.add(Math.max(idealRemaining, 0));
            if (d.isAfter(today) && !d.equals(today)) {
                actual.add(null);
            } else {
                // 真实累计完成量：取 <=d 的最新累计值
                long cumulativeDoneUpToD = completedBeforeStart;
                for (Map.Entry<LocalDate, Long> e : cumulativeByDate.entrySet()) {
                    if (!e.getKey().isAfter(d)) {
                        cumulativeDoneUpToD = e.getValue();
                    } else {
                        break;
                    }
                }
                long remaining = Math.max(totalEffort - cumulativeDoneUpToD, 0);
                actual.add((int) remaining);
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("dates", dates);
        data.put("ideal", ideal);
        data.put("actual", actual);
        data.put("reason", "OK");
        data.put("totalEffort", totalEffort);
        data.put("doneEffort", doneEffort);
        return Result.success(data);
    }
}