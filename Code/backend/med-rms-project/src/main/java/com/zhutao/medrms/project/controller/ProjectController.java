package com.zhutao.medrms.project.controller;

import com.zhutao.medrms.common.annotation.AuditLog;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.project.domain.entity.ComplianceTemplate;
import com.zhutao.medrms.project.domain.entity.Project;
import com.zhutao.medrms.project.service.ComplianceTemplateService;
import com.zhutao.medrms.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "项目管理", description = "项目CRUD接口")
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ComplianceTemplateService templateService;

    /**
     * R161 修复（F7）：/projects 加分页支持
     *   修复前：单次返回全表（无分页），opencode 测试发现 697ms（数据增长后）
     *   修复：默认 page=0, size=20；size 上限 100 防止恶意请求
     */
    @Operation(summary = "获取项目列表（分页）")
    @GetMapping
    public Result<List<Project>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        if (size < 1) size = 20;
        if (size > 100) size = 100;
        if (page < 0) page = 0;
        return Result.success(projectService.list(status, page, size));
    }

    @Operation(summary = "获取项目详情")
    @GetMapping("/{id}")
    public Result<Project> getById(@PathVariable Long id) {
        return Result.success(projectService.getById(id));
    }

    @AuditLog(eventType = "CREATE", entityType = "PROJECT", operation = "创建项目")
    @Operation(summary = "创建项目")
    @PostMapping
    public Result<Project> create(@RequestBody Project project) {
        return Result.success(projectService.create(project));
    }

    @AuditLog(eventType = "MODIFY", entityType = "PROJECT", operation = "更新项目", entityIdSpel = "#id")
    @Operation(summary = "更新项目")
    @PutMapping("/{id}")
    public Result<Project> update(@PathVariable Long id, @RequestBody Project project) {
        return Result.success(projectService.update(id, project));
    }

    // ===== R175 FR-2.11: 项目克隆 =====
    @AuditLog(eventType = "CREATE", entityType = "PROJECT", operation = "克隆项目", entityIdSpel = "#id")
    @Operation(summary = "克隆项目（FR-2.11）")
    @PostMapping("/{id}/clone")
    public Result<Project> cloneProject(@PathVariable Long id, @RequestParam(required = false) String newName,
                                        @RequestParam(required = false) Long operatorId,
                                        @RequestParam(required = false) String operatorName) {
        return Result.success(projectService.cloneProject(id, newName, operatorId, operatorName));
    }

    // ===== R175 FR-2.16: 健康度评分 =====
    @Operation(summary = "获取项目健康度评分（FR-2.16）")
    @GetMapping("/{id}/health-score")
    public Result<java.util.Map<String, Object>> getHealthScore(@PathVariable Long id) {
        return Result.success(projectService.calculateHealthScore(id));
    }

    // ===== R175 FR-2.12: Excel 导出/导入 =====
    @Operation(summary = "导出项目计划为 JSON（FR-2.12）")
    @GetMapping("/{id}/export")
    public Result<String> exportProjectPlan(@PathVariable Long id) {
        try {
            String json = projectService.exportProjectPlanAsJson(id);
            return Result.success(json);
        } catch (Exception e) {
            log.error("导出项目计划失败, projectId={}", id, e);
            return Result.error(500, "导出失败，请稍后重试或联系管理员");
        }
    }

    @AuditLog(eventType = "CREATE", entityType = "PROJECT", operation = "导入任务到项目", entityIdSpel = "#id")
    @Operation(summary = "导入任务到项目（FR-2.12，JSON格式）")
    @PostMapping("/{id}/import-tasks")
    public Result<Void> importTasks(@PathVariable Long id, @RequestBody java.util.List<Map<String, Object>> tasks,
                                    @RequestParam(required = false) Long operatorId,
                                    @RequestParam(required = false) String operatorName) {
        projectService.importTasks(id, tasks, operatorId, operatorName);
        return Result.success(null);
    }

    // ===== R175 FR-2.12: Excel 导出 (.xlsx) =====
    @Operation(summary = "导出项目计划为 Excel（FR-2.12）")
    @GetMapping("/{id}/export/excel")
    public void exportExcel(@PathVariable Long id, HttpServletResponse response) throws IOException {
        byte[] bytes = projectService.exportExcelAsXlsx(id);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=project_" + id + ".xlsx");
        response.getOutputStream().write(bytes);
    }

    // ===== R175 FR-2.11: 保存为项目模板 =====
    @AuditLog(eventType = "CREATE", entityType = "COMPLIANCE_TEMPLATE", operation = "项目保存为模板", entityIdSpel = "#id")
    @Operation(summary = "将项目保存为模板（FR-2.11）")
    @PostMapping("/{id}/save-as-template")
    public Result<ComplianceTemplate> saveAsTemplate(@PathVariable Long id,
                                                      @RequestParam String templateName,
                                                      @RequestParam(required = false) Long operatorId,
                                                      @RequestParam(required = false) String operatorName) {
        return Result.success(projectService.saveProjectAsTemplate(id, templateName, operatorId, operatorName));
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
    public Result<List<ComplianceTemplate>> listTemplates(
            @RequestParam(required = false) String category) {
        if (category != null && !category.isBlank()) {
            return Result.success(templateService.listByCategory(category));
        }
        return Result.success(templateService.listAll());
    }

    @AuditLog(eventType = "MODIFY", entityType = "PROJECT", operation = "应用合规模板", entityIdSpel = "#id")
    @Operation(summary = "应用合规模板到项目")
    @PostMapping("/{id}/apply-template")
    public Result<Project> applyTemplate(@PathVariable Long id, @RequestParam Long templateId) {
        return Result.success(templateService.applyTemplateToProject(id, templateId));
    }

    @AuditLog(eventType = "CREATE", entityType = "COMPLIANCE_TEMPLATE", operation = "创建合规模板")
    @Operation(summary = "创建自定义合规模板")
    @PostMapping("/templates")
    public Result<ComplianceTemplate> createTemplate(
            @RequestBody ComplianceTemplate template,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userName) {
        return Result.success(templateService.createCustom(template, userId, userName));
    }

    @AuditLog(eventType = "MODIFY", entityType = "COMPLIANCE_TEMPLATE", operation = "更新合规模板", entityIdSpel = "#id")
    @Operation(summary = "更新自定义合规模板")
    @PutMapping("/templates/{id}")
    public Result<ComplianceTemplate> updateTemplate(@PathVariable Long id, @RequestBody ComplianceTemplate template) {
        return Result.success(templateService.updateCustom(id, template));
    }

    @AuditLog(eventType = "DELETE", entityType = "COMPLIANCE_TEMPLATE", operation = "删除合规模板", entityIdSpel = "#id")
    @Operation(summary = "删除自定义合规模板（软删除）")
    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteCustom(id);
        return Result.success(null);
    }
}