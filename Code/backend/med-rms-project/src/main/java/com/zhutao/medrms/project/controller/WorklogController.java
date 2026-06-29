package com.zhutao.medrms.project.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.project.domain.entity.Worklog;
import com.zhutao.medrms.project.service.WorklogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "工时统计", description = "FR-2.9 工时填报与汇总")
@RestController
@RequestMapping("/worklog")
@RequiredArgsConstructor
public class WorklogController {

    private final WorklogService worklogService;

    @Operation(summary = "提交工时")
    @PostMapping
    public Result<Worklog> create(@RequestBody Worklog log) {
        return Result.success(worklogService.create(log));
    }

    @Operation(summary = "工时汇总（按项目/人员/需求筛选）")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) Long projectId,
                                                @RequestParam(required = false) Long workerId,
                                                @RequestParam(required = false) Long requirementId) {
        return Result.success(worklogService.summary(projectId, workerId, requirementId));
    }
}
