package com.zhutao.medrms.compliance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.compliance.domain.entity.PrCorrection;
import com.zhutao.medrms.compliance.service.PrCorrectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "PR 纠正措施", description = "ISO 13485 §8.5.2 CAPA 子表")
@RestController
@RequestMapping("/compliance/pr-correction")
@RequiredArgsConstructor
public class PrCorrectionController {

    private final PrCorrectionService service;

    @Operation(summary = "创建纠正措施")
    @PostMapping
    public Result<PrCorrection> create(@RequestBody CreateRequest req) {
        return Result.success(service.create(req.getProblemReportId(), req.getAction(),
                req.getOwnerId(), req.getDueDate()));
    }

    @Operation(summary = "完成纠正措施")
    @PostMapping("/{id}/complete")
    public Result<PrCorrection> complete(@PathVariable Long id, @RequestParam String effectiveness) {
        return Result.success(service.complete(id, effectiveness));
    }

    @Operation(summary = "验证纠正措施（必须不同人）")
    @PostMapping("/{id}/verify")
    public Result<PrCorrection> verify(@PathVariable Long id, @RequestParam Long verifierId) {
        return Result.success(service.verify(id, verifierId));
    }

    @Operation(summary = "按 ID 查询")
    @GetMapping("/{id}")
    public Result<PrCorrection> get(@PathVariable Long id) {
        return Result.success(service.getById(id));
    }

    @Operation(summary = "按问题报告分页查询")
    @GetMapping("/by-report/{problemReportId}")
    public Result<IPage<PrCorrection>> listByReport(@PathVariable Long problemReportId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.listByReport(problemReportId, page, size));
    }

    @lombok.Data
    public static class CreateRequest {
        private Long problemReportId;
        private String action;
        private Long ownerId;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime dueDate;
    }
}
