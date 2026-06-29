package com.zhutao.medrms.compliance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.compliance.domain.entity.SafetyClassification;
import com.zhutao.medrms.compliance.service.SafetyClassificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "安全分类", description = "IEC 62304 §5 软件安全分类")
@RestController
@RequestMapping("/compliance/safety-classification")
@RequiredArgsConstructor
public class SafetyClassificationController {

    private final SafetyClassificationService service;

    @Operation(summary = "创建软件安全分类")
    @PostMapping
    public Result<SafetyClassification> create(@RequestBody CreateRequest req) {
        return Result.success(service.create(req.getProjectId(), req.getSafetyClass(),
                req.getRationale(), req.getRemarks()));
    }

    @Operation(summary = "复核并锁定（必须不同人）")
    @PostMapping("/{id}/review")
    public Result<SafetyClassification> review(@PathVariable Long id, @RequestParam Long reviewerId) {
        return Result.success(service.review(id, reviewerId));
    }

    @Operation(summary = "按 ID 查询")
    @GetMapping("/{id}")
    public Result<SafetyClassification> get(@PathVariable Long id) {
        return Result.success(service.getById(id));
    }

    @Operation(summary = "分页查询")
    @GetMapping
    public Result<IPage<SafetyClassification>> list(@RequestParam(required = false) Long projectId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.list(projectId, page, size));
    }

    @lombok.Data
    public static class CreateRequest {
        private Long projectId;
        private String safetyClass;
        private String rationale;
        private String remarks;
    }
}
