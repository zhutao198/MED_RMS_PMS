package com.zhutao.medrms.project.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.project.domain.entity.ProjectDeliverable;
import com.zhutao.medrms.project.service.ProjectDeliverableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目交付物接口（R92 新增）
 * 之前端 /projects/{id}/deliverables 是"功能开发中"，现在完整实现
 */
@Tag(name = "项目交付物", description = "项目交付物登记、状态、删除")
@RestController
@RequestMapping("/projects/{projectId}/deliverables")
@RequiredArgsConstructor
public class ProjectDeliverableController {

    private final ProjectDeliverableService service;

    @Operation(summary = "查询项目下所有交付物")
    @GetMapping
    public Result<List<ProjectDeliverable>> list(@PathVariable Long projectId) {
        return Result.success(service.listByProject(projectId));
    }

    @Operation(summary = "登记新交付物")
    @PostMapping
    public Result<ProjectDeliverable> create(@PathVariable Long projectId, @RequestBody ProjectDeliverable d) {
        return Result.success(service.create(projectId, d));
    }

    @Operation(summary = "更新交付物状态（如标记完成）")
    @PutMapping("/{id}/status")
    public Result<ProjectDeliverable> updateStatus(@PathVariable Long projectId,
                                                  @PathVariable Long id,
                                                  @RequestParam String status) {
        return Result.success(service.updateStatus(projectId, id, status));
    }

    @Operation(summary = "删除交付物（软删）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long projectId, @PathVariable Long id) {
        service.delete(projectId, id);
        return Result.success();
    }
}
