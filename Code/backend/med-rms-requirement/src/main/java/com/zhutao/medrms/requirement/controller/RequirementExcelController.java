package com.zhutao.medrms.requirement.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.service.RequirementExcelImportService;
import com.zhutao.medrms.requirement.service.RequirementExcelTemplateService;
import com.zhutao.medrms.requirement.service.RequirementExcelValidator;
import com.zhutao.medrms.requirement.service.RequirementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * R208 v1.65: 四层需求 Excel 导入 Controller（FR-1.13 PRD §7.8.3 + FR-1.13 数据迁移工具）
 *
 * 端点：
 *  - GET  /requirements/excel/template/{type}        下载模板
 *  - POST /requirements/excel/import/{type}          上传导入
 *
 * 关联：
 *  - R182 需求池导入（POST /requirement-pool/import）— 单 List<Map> 入库，无 Excel 解析
 *  - R199 ProductExportService — POI 复用模板
 */
@Slf4j
@Tag(name = "需求 Excel 导入", description = "FR-1.13 四层需求 Excel 批量导入")
@RestController
@RequestMapping("/requirements/excel")
@RequiredArgsConstructor
public class RequirementExcelController {

    private final RequirementExcelImportService importService;
    private final RequirementExcelTemplateService templateService;
    private final RequirementExcelValidator validator;
    private final RequirementService requirementService;

    /**
     * 下载指定层级的 Excel 模板
     * @param type URS/PRS/SRS/DRS
     */
    @Operation(summary = "下载 Excel 模板")
    @GetMapping("/template/{type}")
    public void downloadTemplate(@PathVariable String type, HttpServletResponse response) throws IOException {
        log.info("R208 模板下载: type={}", type);
        templateService.downloadTemplate(response, type.toUpperCase());
    }

    /**
     * 上传 Excel 批量导入需求
     * @param type      URS/PRS/SRS/DRS
     * @param projectId 所属项目 ID（必填）
     * @return ImportResult {total, success, failed:[{row, error}]}
     */
    @Operation(summary = "上传 Excel 批量导入")
    @PostMapping("/import/{type}")
    public Result<Map<String, Object>> importExcel(
            @PathVariable String type,
            @RequestParam Long projectId,
            @RequestParam("file") MultipartFile file) throws IOException {
        log.info("R208 Excel 导入: type={}, projectId={}, filename={}, size={}KB",
                type, projectId, file.getOriginalFilename(), file.getSize() / 1024);

        String upperType = type.toUpperCase();
        if (projectId == null) {
            throw new IllegalArgumentException("projectId 必填");
        }

        // 1. 解析 Excel
        RequirementExcelImportService.ParseResult parseResult = importService.parse(file, upperType);

        // 2. 逐行校验 + 入库
        List<Map<String, Object>> failed = new ArrayList<>();
        List<Requirement> toCreate = new ArrayList<>();
        Map<Integer, List<String>> upstreamMap = new LinkedHashMap<>(); // row → upstreamNos

        for (int i = 0; i < parseResult.rows().size(); i++) {
            Map<String, String> row = parseResult.rows().get(i);
            int rowNumber = i + 2; // Excel 行号（1=表头）
            RequirementExcelValidator.ValidationResult vr =
                validator.validate(rowNumber, row, projectId, upperType);

            if (vr.isValid()) {
                toCreate.add(vr.requirement());
                if (vr.upstreamNos() != null && !vr.upstreamNos().isEmpty()) {
                    upstreamMap.put(toCreate.size() - 1, vr.upstreamNos());
                }
            } else {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("row", rowNumber);
                failure.put("title", row.get("title"));
                failure.put("errors", vr.errors());
                failed.add(failure);
            }
        }

        // 3. 批量入库（部分成功）
        List<Requirement> created = requirementService.createBatchRequirements(toCreate);

        // 4. 收集入库失败（与校验失败合并）
        int successCount = created.size();
        int totalCount = parseResult.rows().size();

        // 5. 追溯关系重建（占位 — 实际由 TraceabilityService.rebuildFromImport 实现，后续 R208.1 提交）
        // TODO R208.1: 追溯重建 TraceabilityService.rebuildFromImport(created, upstreamMap)

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", totalCount);
        result.put("success", successCount);
        result.put("failed", failed);
        result.put("createdIds", created.stream().map(Requirement::getId).toList());
        result.put("packageType", upperType);

        log.info("R208 导入完成: total={}, success={}, failed={}", totalCount, successCount, failed.size());
        return Result.success(result);
    }
}
