package com.zhutao.medrms.admin.controller;

import com.zhutao.medrms.admin.domain.entity.MigrationJob;
import com.zhutao.medrms.admin.service.DataMigrationService;
import com.zhutao.medrms.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "数据迁移", description = "FR-1.13 数据导入/导出")
@RestController
@RequestMapping("/admin/migration")
@RequiredArgsConstructor
public class MigrationController {

    private final DataMigrationService dataMigrationService;

    @Operation(summary = "查询迁移任务列表")
    @GetMapping("/jobs")
    public Result<List<MigrationJob>> listJobs() {
        return Result.success(dataMigrationService.listJobs());
    }

    @Operation(summary = "查询迁移任务详情")
    @GetMapping("/jobs/{id}")
    public Result<MigrationJob> getJob(@PathVariable Long id) {
        return Result.success(dataMigrationService.getJob(id));
    }

    @Operation(summary = "导入需求（JSON 文本）")
    @PostMapping("/import/requirements/json")
    public Result<MigrationJob> importRequirementsJson(@RequestBody MigrationJsonRequest req) {
        return Result.success(dataMigrationService.importRequirements(
                req.getSourceName(), req.getContent(), req.getOperatorId()));
    }

    @Operation(summary = "导入需求（上传 JSON 文件）")
    @PostMapping("/import/requirements/upload-json")
    public Result<MigrationJob> uploadJson(@RequestParam("file") MultipartFile file,
                                            @RequestParam(required = false) String sourceName,
                                            @RequestParam(required = false) Long operatorId) throws Exception {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        String name = sourceName != null ? sourceName : file.getOriginalFilename();
        return Result.success(dataMigrationService.importRequirements(name, content, operatorId));
    }

    @Operation(summary = "导入需求（上传 CSV 文件）")
    @PostMapping("/import/requirements/upload-csv")
    public Result<MigrationJob> uploadCsv(@RequestParam("file") MultipartFile file,
                                            @RequestParam(required = false) String sourceName,
                                            @RequestParam(required = false) Long operatorId) throws Exception {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        String name = sourceName != null ? sourceName : file.getOriginalFilename();
        return Result.success(dataMigrationService.importRequirementsCsv(name, content, operatorId));
    }

    @Data
    public static class MigrationJsonRequest {
        private String sourceName;
        private String content;
        private Long operatorId;
    }
}
