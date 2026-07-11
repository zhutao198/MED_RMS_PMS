package com.zhutao.medrms.project.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    @PutMapping("/tasks/{id}")
    public Result<Task> updateTask(@PathVariable Long id, @RequestBody Task updates,
                                   @RequestParam(required = false) Long operatorId,
                                   @RequestParam(required = false) String operatorName) {
        Task task = ganttService.getTaskById(id);
        if (updates.getTitle() != null) task.setTitle(updates.getTitle());
        if (updates.getStartDate() != null) task.setStartDate(updates.getStartDate());
        if (updates.getEndDate() != null) task.setEndDate(updates.getEndDate());
        if (updates.getStatus() != null) task.setStatus(updates.getStatus());
        if (updates.getAssigneeId() != null) task.setAssigneeId(updates.getAssigneeId());
        if (updates.getAssigneeName() != null) task.setAssigneeName(updates.getAssigneeName());
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
        if (project == null || project.getStartDate() == null) {
            return Result.success(Map.of("dates", List.of(), "ideal", List.of(), "actual", List.of()));
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
        if (totalEffort == 0) {
            return Result.success(Map.of("dates", List.of(), "ideal", List.of(), "actual", List.of()));
        }

        double dailyRate = (double) totalEffort / totalDays;
        List<String> dates = new ArrayList<>();
        List<Integer> ideal = new ArrayList<>();
        List<Integer> actual = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (long i = 0; i <= totalDays; i++) {
            LocalDate d = start.plusDays(i);
            dates.add(d.toString());
            int idealRemaining = (int) Math.round(totalEffort - dailyRate * i);
            ideal.add(Math.max(idealRemaining, 0));
            if (d.isAfter(today) && !d.equals(today)) {
                actual.add(null);
            } else {
                double progress = totalDays > 0 ? (double) ChronoUnit.DAYS.between(start, d) / totalDays : 0;
                long completed = Math.round(totalEffort * Math.min(progress, 1.0));
                long remaining = d.isEqual(today) ? (totalEffort - doneEffort) : Math.max(totalEffort - completed, 0);
                actual.add((int) Math.max(remaining, 0));
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("dates", dates);
        data.put("ideal", ideal);
        data.put("actual", actual);
        return Result.success(data);
    }
}