package com.zhutao.medrms.requirement.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.requirement.domain.entity.RequirementPool;
import com.zhutao.medrms.requirement.mapper.RequirementPoolMapper;
import com.zhutao.medrms.requirement.service.RequirementPoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;

@Tag(name = "需求收集池", description = "多渠道需求收集接口")
@RestController
@RequestMapping("/requirement-pool")
@RequiredArgsConstructor
public class RequirementPoolController {

    private final RequirementPoolMapper poolMapper;
    private final RequirementPoolService poolService;

    @Operation(summary = "获取收集池列表")
    @GetMapping
    public Result<List<RequirementPool>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate createdAtStart,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate createdAtEnd,
            @RequestParam(required = false) String keyword) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RequirementPool>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(RequirementPool::getStatus, status);
        }
        if (source != null && !source.isBlank()) {
            wrapper.eq(RequirementPool::getSource, source);
        }
        if (createdAtStart != null) {
            wrapper.ge(RequirementPool::getCreatedAt, createdAtStart.atStartOfDay());
        }
        if (createdAtEnd != null) {
            wrapper.le(RequirementPool::getCreatedAt, createdAtEnd.plusDays(1).atStartOfDay());
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                .like(RequirementPool::getId, keyword)
                .or().like(RequirementPool::getTitle, keyword)
                .or().like(RequirementPool::getRawDescription, keyword)
                .or().like(RequirementPool::getProposer, keyword)
            );
        }
        wrapper.orderByDesc(RequirementPool::getId);
        return Result.success(poolMapper.selectList(wrapper));
    }

    @Operation(summary = "添加需求到收集池")
    @PostMapping
    public Result<String> add(@RequestBody AddRequest request) {
        String id = poolService.addToPool(request.getSource(), request.getSourceNo(),
            request.getRawDescription(), request.getCreatedBy(),
            request.getTitle(), request.getPriority(),
            request.getBusinessScenario(), request.getCompetitiveAnalysis(),
            request.getProposer());
        return Result.success(id);
    }

    @Operation(summary = "转换为URS")
    @Transactional
    @PostMapping("/{id}/convert")
    public Result<Long> convert(@PathVariable String id, @RequestBody ConvertRequest request) {
        var urs = poolService.convertToUrs(id, request.getProjectId(), request.getPriority());
        return Result.success(urs.getId());
    }

    @Operation(summary = "批量导入需求到收集池")
    @PostMapping("/import")
    public Result<Integer> importBatch(@RequestBody List<Map<String, Object>> items) {
        int count = poolService.importFromList(items);
        return Result.success(count);
    }

    @Operation(summary = "拒绝需求池条目（标记为 REJECTED）")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable String id, @RequestBody Map<String, String> body) {
        poolService.rejectPoolItem(id, body.get("reason"));
        return Result.success();
    }

    @Operation(summary = "删除需求池条目（物理删除，仅限 REJECTED 状态）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        poolService.deletePoolItem(id);
        return Result.success();
    }

    @lombok.Data
    public static class AddRequest {
        private String source;
        private String sourceNo;
        private String rawDescription;
        private String title;
        private String priority;
        private String businessScenario;
        private String competitiveAnalysis;
        private String proposer;
        private Long projectId;
        private Long createdBy;
    }

    @lombok.Data
    public static class ConvertRequest {
        private Long projectId;
        private String priority;
    }
}