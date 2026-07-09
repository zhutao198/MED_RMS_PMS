package com.zhutao.medrms.requirement.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.requirement.service.AIService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "AI 需求分析", description = "需求质量评分、相似度检测等规则引擎分析服务")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AIController {

    private final AIService aiService;

    @Operation(summary = "分析需求质量", description = "对需求标题、描述、验收标准等进行规则评分，返回评分/等级/问题清单")
    @PostMapping("/requirement/analyze")
    public Result<Map<String, Object>> analyzeRequirement(@RequestBody Map<String, String> body) {
        Map<String, Object> result = aiService.analyzeRequirement(
                body.get("title"),
                body.get("description"),
                body.get("acceptanceCriteria"),
                body.get("requirementType"),
                body.get("priority"),
                body.get("safetyClass")
        );
        return Result.success(result);
    }

    @Operation(summary = "计算需求相似度", description = "基于 Jaccard 相似系数比较两个需求标题的相似度")
    @PostMapping("/requirement/similarity")
    public Result<Map<String, Object>> computeSimilarity(@RequestBody Map<String, String> body) {
        Map<String, Object> result = aiService.computeSimilarity(
                body.get("title1"),
                body.get("title2")
        );
        return Result.success(result);
    }
}
