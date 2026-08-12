package com.zhutao.medrms.project.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.project.domain.entity.Task;
import com.zhutao.medrms.project.service.RequirementTaskService;
import com.zhutao.medrms.project.service.RequirementTaskService.TaskDraft;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 需求→任务转化接口 - FR-1.10
 */
@Tag(name = "需求→任务", description = "SRS/DRS 拆解为任务，状态双向联动（FR-1.10）")
@RestController
@RequestMapping("/requirement-tasks")
@RequiredArgsConstructor
public class RequirementTaskController {

    private final RequirementTaskService service;

    @Operation(summary = "根据需求类型生成任务草稿（不写入）")
    @GetMapping("/drafts/{requirementId}")
    public Result<List<TaskDraft>> generateDrafts(@PathVariable Long requirementId) {
        return Result.success(service.generateDrafts(requirementId));
    }

    @Operation(summary = "将需求拆解为多个任务（确认后写入）")
    @PostMapping("/convert/{requirementId}")
    public Result<List<Task>> convert(@PathVariable Long requirementId, @RequestBody List<TaskDraft> drafts) {
        return Result.success(service.convertRequirementToTasks(requirementId, drafts));
    }

    @Operation(summary = "查询需求关联的所有任务")
    @GetMapping("/by-requirement/{requirementId}")
    public Result<List<Task>> getTasks(@PathVariable Long requirementId) {
        return Result.success(service.getTasksByRequirement(requirementId));
    }

    @Operation(summary = "更新任务状态（自动同步需求状态）")
    @PutMapping("/{taskId}/status")
    public Result<Task> updateStatus(@PathVariable Long taskId, @RequestParam String status) {
        return Result.success(service.updateTaskStatus(taskId, status));
    }

    // 差异 #6：任务实际工时填报入口（PRD §7.7.1 工时超支联动）
    @Operation(summary = "填报/更新任务实际工时（自动触发工时超支联动）")
    @PatchMapping("/{taskId}/actual-hours")
    public Result<Task> updateActualHours(@PathVariable Long taskId, @RequestParam Integer actualHours) {
        return Result.success(service.updateTaskActualHours(taskId, actualHours));
    }

    @Operation(summary = "需求转化进度统计")
    @GetMapping("/progress/{requirementId}")
    public Result<Map<String, Object>> getProgress(@PathVariable Long requirementId) {
        return Result.success(service.getRequirementProgress(requirementId));
    }

    // R268：批量需求进度统计（避免 N+1）
    @Operation(summary = "批量需求进度统计（按 ids 列表）")
    @GetMapping("/progress/batch")
    public Result<Map<Long, Map<String, Object>>> getProgressBatch(@RequestParam("ids") java.util.List<Long> ids) {
        return Result.success(service.getRequirementProgressBatch(ids));
    }

    // R92 修复：原后端无 /requirement-tasks/by-project/{id} 端点（SY0301）
    // 新增：按项目聚合工时（FR-2.8 资源管理依赖）
    @Operation(summary = "按项目查询所有任务（用于资源管理工时聚合）")
    @GetMapping("/by-project/{projectId}")
    public Result<List<Task>> listByProject(@PathVariable Long projectId) {
        return Result.success(service.listTasksByProject(projectId));
    }

    // P2 新增：可转化为任务的需求候选列表（任务转化页前端选单）
    @Operation(summary = "列出项目下可转化为任务的需求（SRS/DRS、未基线化、未拆解过）")
    @GetMapping("/candidates")
    public Result<List<Map<String, Object>>> listCandidates(@RequestParam Long projectId) {
        return Result.success(service.listConvertibleRequirements(projectId));
    }
}
