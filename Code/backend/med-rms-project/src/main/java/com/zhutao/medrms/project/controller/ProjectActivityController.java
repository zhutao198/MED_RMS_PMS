package com.zhutao.medrms.project.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.project.domain.entity.ProjectActivity;
import com.zhutao.medrms.project.service.GanttService;
import com.zhutao.medrms.project.service.ProjectActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "项目活动流", description = "项目活动流与资源调整建议（FR-2.13/FR-2.8）")
@RestController
@RequestMapping("/project-activity")
@RequiredArgsConstructor
public class ProjectActivityController {

    private final ProjectActivityService activityService;
    private final GanttService ganttService;

    @Operation(summary = "获取项目活动流（FR-2.13）")
    @GetMapping("/{projectId}")
    public Result<List<ProjectActivity>> listActivities(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return Result.success(activityService.listByProject(projectId, page, size));
    }

    @Operation(summary = "获取资源调整建议（FR-2.8）")
    @GetMapping("/suggest-adjustments")
    public Result<List<Map<String, Object>>> suggestAdjustments(@RequestParam Long assigneeId) {
        return Result.success(ganttService.suggestAdjustments(assigneeId));
    }
}
