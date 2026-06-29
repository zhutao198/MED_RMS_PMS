package com.zhutao.medrms.requirement.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.requirement.service.QualityScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "需求质量评分", description = "FR-2.4 4 维度智能评分")
@RestController
@RequestMapping("/requirements/quality")
@RequiredArgsConstructor
public class QualityScoreController {

    private final QualityScoreService qualityScoreService;

    @Operation(summary = "评分单个需求")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> score(@PathVariable Long id) {
        return Result.success(qualityScoreService.score(id));
    }

    @Operation(summary = "批量评分（可选 projectId）")
    @GetMapping
    public Result<List<Map<String, Object>>> scoreAll(@RequestParam(required = false) Long projectId) {
        return Result.success(qualityScoreService.scoreAll(projectId));
    }
}
