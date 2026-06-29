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

import java.util.List;

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
            @RequestParam(required = false) String source) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RequirementPool>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(RequirementPool::getStatus, status);
        }
        if (source != null && !source.isBlank()) {
            wrapper.eq(RequirementPool::getSource, source);
        }
        wrapper.orderByDesc(RequirementPool::getCreatedAt);
        return Result.success(poolMapper.selectList(wrapper));
    }

    @Operation(summary = "添加需求到收集池")
    @PostMapping
    public Result<Long> add(@RequestBody AddRequest request) {
        Long id = poolService.addToPool(request.getSource(), request.getSourceNo(),
            request.getRawDescription(), request.getCreatedBy());
        return Result.success(id);
    }

    @Operation(summary = "转换为URS")
    @Transactional
    @PostMapping("/{id}/convert")
    public Result<Long> convert(@PathVariable Long id, @RequestBody ConvertRequest request) {
        var urs = poolService.convertToUrs(id, request.getProjectId(), request.getPriority());
        return Result.success(urs.getId());
    }

    @lombok.Data
    public static class AddRequest {
        private String source;
        private String sourceNo;
        private String rawDescription;
        private String title;
        private String priority;
        private Long projectId;
        private Long createdBy;
    }

    @lombok.Data
    public static class ConvertRequest {
        private Long projectId;
        private String priority;
    }
}