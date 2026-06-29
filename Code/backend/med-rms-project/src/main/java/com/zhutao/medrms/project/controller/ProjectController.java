package com.zhutao.medrms.project.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.project.domain.entity.ComplianceTemplate;
import com.zhutao.medrms.project.domain.entity.Project;
import com.zhutao.medrms.project.service.ComplianceTemplateService;
import com.zhutao.medrms.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "项目管理", description = "项目CRUD接口")
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ComplianceTemplateService templateService;

    @Operation(summary = "获取项目列表")
    @GetMapping
    public Result<List<Project>> list(@RequestParam(required = false) String status) {
        return Result.success(projectService.list(status));
    }

    @Operation(summary = "获取项目详情")
    @GetMapping("/{id}")
    public Result<Project> getById(@PathVariable Long id) {
        return Result.success(projectService.getById(id));
    }

    @Operation(summary = "创建项目")
    @PostMapping
    public Result<Project> create(@RequestBody Project project) {
        return Result.success(projectService.create(project));
    }

    @Operation(summary = "更新项目")
    @PutMapping("/{id}")
    public Result<Project> update(@PathVariable Long id, @RequestBody Project project) {
        return Result.success(projectService.update(id, project));
    }

    // ===== v1.43 P1-9 修复：项目进度聚合 =====
    @Operation(summary = "获取项目整体进度（P1-9 前端 ProjectsList 用）")
    @GetMapping("/{id}/progress")
    public Result<java.util.Map<String, Object>> getProgress(@PathVariable Long id) {
        return Result.success(projectService.getProjectProgress(id));
    }

    // ========== 合规模板 FR-1.9 ==========

    @Operation(summary = "列出所有合规模板（含 4 预设 + 自定义）")
    @GetMapping("/templates")
    public Result<List<ComplianceTemplate>> listTemplates() {
        return Result.success(templateService.listAll());
    }

    @Operation(summary = "应用合规模板到项目")
    @PostMapping("/{id}/apply-template")
    public Result<Project> applyTemplate(@PathVariable Long id, @RequestParam Long templateId) {
        return Result.success(templateService.applyTemplateToProject(id, templateId));
    }

    @Operation(summary = "创建自定义合规模板")
    @PostMapping("/templates")
    public Result<ComplianceTemplate> createTemplate(
            @RequestBody ComplianceTemplate template,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userName) {
        return Result.success(templateService.createCustom(template, userId, userName));
    }

    @Operation(summary = "更新自定义合规模板")
    @PutMapping("/templates/{id}")
    public Result<ComplianceTemplate> updateTemplate(@PathVariable Long id, @RequestBody ComplianceTemplate template) {
        return Result.success(templateService.updateCustom(id, template));
    }

    @Operation(summary = "删除自定义合规模板（软删除）")
    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteCustom(id);
        return Result.success(null);
    }
}