package com.zhutao.medrms.project.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.project.domain.entity.Milestone;
import com.zhutao.medrms.project.domain.entity.Task;
import com.zhutao.medrms.project.mapper.MilestoneMapper;
import com.zhutao.medrms.project.mapper.TaskMapper;
import com.zhutao.medrms.project.service.GanttService;
import com.zhutao.medrms.project.service.TaskPredecessorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
}