package com.zhutao.medrms.requirement.controller;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zhutao.medrms.common.annotation.AuditLog;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.result.PageResult;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.common.util.SecurityUtils;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.domain.entity.RequirementVersion;
import com.zhutao.medrms.requirement.domain.entity.Review;
import com.zhutao.medrms.requirement.service.RequirementService;
import com.zhutao.medrms.requirement.service.RequirementVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "需求管理", description = "需求管理相关接口")
@RestController
@RequestMapping("/requirements")
@RequiredArgsConstructor
public class RequirementController {

    private final RequirementService requirementService;
    private final RequirementVersionService versionService;

    @Operation(summary = "获取需求列表")
    @GetMapping
    public Result<IPage<Requirement>> listRequirements(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        return Result.success(requirementService.listRequirements(projectId, type, status, priority, keyword, page, size));
    }

    @Operation(summary = "看板视图：按状态分组（FR-1.1）")
    @GetMapping("/kanban")
    public Result<java.util.Map<String, java.util.List<Requirement>>> kanban(
            @RequestParam(value = "projectId", required = false) Long projectId) {
        return Result.success(requirementService.listGroupedByStatus(projectId));
    }

    /**
     * 需求统计聚合 (R115 P0-02 修复)
     * 返回 5 张统计卡所需数据：总数/草稿/已批准/已实现/已关闭
     * 前端 RequirementList.vue 调用此端点替代本地 size=1000 聚合
     */
    @Operation(summary = "需求统计聚合（5 张统计卡）")
    @GetMapping("/stats")
    public Result<java.util.Map<String, Long>> stats(
            @RequestParam(value = "projectId", required = false) Long projectId) {
        return Result.success(requirementService.getStats(projectId));
    }

    @Operation(summary = "获取需求详情")
    @GetMapping("/{id}")
    public Result<Requirement> getRequirement(@PathVariable Long id) {
        return Result.success(requirementService.getRequirementById(id));
    }

    @Operation(summary = "创建需求")
    @PostMapping
    @AuditLog(eventType = "CREATE", entityType = "REQUIREMENT", operation = "创建需求")
    public Result<Requirement> createRequirement(@RequestBody Requirement requirement) {
        return Result.success(requirementService.createRequirement(requirement, null));
    }

    // ===== v1.43 P1-2 修复：批量创建（导入用）=====
    @Operation(summary = "批量创建需求（导入）")
    @PostMapping("/batch")
    @AuditLog(eventType = "CREATE", entityType = "REQUIREMENT", operation = "批量导入需求")
    public Result<List<Requirement>> batchCreate(@RequestBody List<Requirement> requirements) {
        return Result.success(requirementService.createBatchRequirements(requirements));
    }

    @Operation(summary = "更新需求")
    @PutMapping("/{id}")
    @AuditLog(eventType = "MODIFY", entityType = "REQUIREMENT", operation = "更新需求", entityIdSpel = "#id")
    public Result<Requirement> updateRequirement(@PathVariable Long id, @RequestBody Requirement updates) {
        return Result.success(requirementService.updateRequirement(id, updates));
    }

    @Operation(summary = "拆解需求")
    @PostMapping("/{id}/decompose")
    @AuditLog(eventType = "CREATE", entityType = "REQUIREMENT", operation = "拆解需求", entityIdSpel = "#id")
    public Result<Requirement> decomposeRequirement(
            @PathVariable Long id,
            @RequestBody Requirement childRequirement) {
        return Result.success(requirementService.decomposeRequirement(id, childRequirement, null));
    }

    @Operation(summary = "发起评审")
    @PostMapping("/{id}/review")
    @AuditLog(eventType = "REVIEW", entityType = "REQUIREMENT", operation = "发起评审", entityIdSpel = "#id")
    public Result<Review> submitForReview(
            @PathVariable Long id,
            @RequestParam Long reviewerId,
            @RequestParam(required = false) String comments) {
        return Result.success(requirementService.submitForReview(id, reviewerId, comments));
    }

    @Operation(summary = "审批需求")
    @PostMapping("/{id}/approve")
    @AuditLog(eventType = "APPROVE", entityType = "REQUIREMENT", operation = "审批需求", entityIdSpel = "#id")
    public Result<Void> approveRequirement(
            @PathVariable Long id,
            @RequestParam String decision,
            @RequestParam Long approverId,
            @RequestParam(required = false) String comments) {
        requirementService.approveRequirement(id, decision, approverId, comments);
        return Result.success();
    }

    @Operation(summary = "基线化需求")
    @PostMapping("/baseline")
    // v1.45 BUG #94 修复：原注解无 entityIdSpel，方法返回 Result<Void>（data=null），
    // 切面 resolveEntityId() 兜底返回 null，导致 t_audit_log.entity_id NOT NULL 约束失败。
    // 修复：显式用 #baselineId 作为 entityId 写入审计日志。
    @AuditLog(eventType = "STATUS_CHANGE", entityType = "BASELINE", operation = "基线化需求", entityIdSpel = "#baselineId")
    public Result<Void> baselineRequirements(
            @RequestParam Long baselineId,
            @RequestBody List<Long> requirementIds) {
        requirementService.baselineRequirements(baselineId, requirementIds);
        return Result.success();
    }

    @Operation(summary = "获取需求版本历史")
    @GetMapping("/{id}/versions")
    public Result<List<RequirementVersion>> getRequirementVersions(@PathVariable Long id) {
        return Result.success(versionService.getVersionsByRequirementId(id));
    }

    /**
     * v1.52 新增：手动创建需求版本快照
     * 入参：changeSummary 字段是 JSON 字符串，包含 summary（变更摘要）+ cti（关联标准代码数组，IEC 62304 / ISO 14971 / IEC 60601-1）
     * 业务行为：
     *   1) 取当前需求主表 + CTI 子表数据做快照（snapshot）
     *   2) 生成语义化版本号 v(major.minor)，默认 minor +1
     *   3) 把 cti 数组合并进 changeSummary JSON 一并落库
     */
    @Operation(summary = "手动创建需求版本快照（带 CTI 标准关联）")
    @PostMapping("/{id}/versions")
    @AuditLog(eventType = "CREATE", entityType = "REQUIREMENT_VERSION", operation = "创建需求版本", entityIdSpel = "#id")
    public Result<RequirementVersion> createRequirementVersion(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Object changeSummaryRaw = body == null ? null : body.get("changeSummary");
        if (changeSummaryRaw == null || (changeSummaryRaw instanceof String s && s.isBlank())) {
            throw BusinessException.param("changeSummary 不能为空");
        }
        // 兼容前端两种入参形态：①直接传 JSON 字符串 ②传对象 {summary, cti}
        String changeSummaryJson;
        if (changeSummaryRaw instanceof String s) {
            changeSummaryJson = s;
        } else {
            changeSummaryJson = JSON.toJSONString(changeSummaryRaw);
        }
        Long operatorId = SecurityUtils.getCurrentUserId();
        RequirementVersion version = versionService.createVersionWithCti(id, changeSummaryJson, operatorId);
        return Result.success(version);
    }

    // ===== v1.47 BUG #126 P0 修复：9 个核心 REST 端点补齐 =====

    @Operation(summary = "撤销评审回到草稿（FR-1.5）")
    @PostMapping("/{id}/revert")
    @AuditLog(eventType = "STATUS_CHANGE", entityType = "REQUIREMENT", operation = "撤销评审", entityIdSpel = "#id")
    public Result<Requirement> revertRequirement(
            @PathVariable Long id,
            @RequestParam Long operatorId,
            @RequestParam(required = false) String reason) {
        return Result.success(requirementService.revertToDraft(id, operatorId, reason));
    }

    @Operation(summary = "开始实施 Approved->InProgress（FR-1.10）")
    @PostMapping("/{id}/start-progress")
    @AuditLog(eventType = "STATUS_CHANGE", entityType = "REQUIREMENT", operation = "开始实施", entityIdSpel = "#id")
    public Result<Requirement> startProgress(@PathVariable Long id) {
        return Result.success(requirementService.startProgress(id));
    }

    @Operation(summary = "开始测试 InProgress->InTest（FR-1.11）")
    @PostMapping("/{id}/start-test")
    @AuditLog(eventType = "STATUS_CHANGE", entityType = "REQUIREMENT", operation = "开始测试", entityIdSpel = "#id")
    public Result<Requirement> startTest(@PathVariable Long id) {
        return Result.success(requirementService.startTest(id));
    }

    @Operation(summary = "验证通过 InTest->Verified（FR-1.12）")
    @PostMapping("/{id}/verify")
    @AuditLog(eventType = "STATUS_CHANGE", entityType = "REQUIREMENT", operation = "验证需求", entityIdSpel = "#id")
    public Result<Requirement> verifyRequirement(
            @PathVariable Long id,
            @RequestParam Long verifierId,
            @RequestParam(required = false) String comments) {
        return Result.success(requirementService.verifyRequirement(id, verifierId, comments));
    }

    @Operation(summary = "撤回需求 Draft/Submitted/InReview->Withdrawn（FR-1.6）")
    @PostMapping("/{id}/withdraw")
    @AuditLog(eventType = "STATUS_CHANGE", entityType = "REQUIREMENT", operation = "撤回需求", entityIdSpel = "#id")
    public Result<Requirement> withdrawRequirement(
            @PathVariable Long id,
            @RequestParam Long operatorId,
            @RequestParam(required = false) String reason) {
        return Result.success(requirementService.withdrawRequirement(id, operatorId, reason));
    }

    @Operation(summary = "追溯变更触发 Suspect 标记（FR-0.10）")
    @PostMapping("/{id}/mark-suspect")
    public Result<Requirement> markSuspect(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        return Result.success(requirementService.markSuspect(id, reason));
    }

    @Operation(summary = "获取需求树（闭包表）")
    @GetMapping("/tree")
    public Result<List<java.util.Map<String, Object>>> getRequirementTree(
            @RequestParam(value = "projectId", required = false) Long projectId) {
        return Result.success(requirementService.getRequirementTree(projectId));
    }

    @Operation(summary = "按用户查询需求")
    @GetMapping("/by-user/{userId}")
    public Result<IPage<Requirement>> listByUser(
            @PathVariable Long userId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        return Result.success(requirementService.listByUser(userId, type, page, size));
    }

    @Operation(summary = "按需求编号查询")
    @GetMapping("/by-no/{requirementNo}")
    public Result<Requirement> getByRequirementNo(@PathVariable String requirementNo) {
        return Result.success(requirementService.getByRequirementNo(requirementNo));
    }

    // ===== v1.47 BUG #128 P0 修复：多 reviewer 评审端点 =====

    @Operation(summary = "多 reviewer 投票（BUG #128 FR-1.4）")
    @PostMapping("/{id}/reviews/vote")
    @AuditLog(eventType = "REVIEW", entityType = "REQUIREMENT", operation = "评审投票", entityIdSpel = "#id")
    public Result<Review> castVote(
            @PathVariable Long id,
            @RequestParam Long reviewerId,
            @RequestParam String decision,
            @RequestParam(required = false) String comments) {
        return Result.success(requirementService.castReviewVote(id, reviewerId, decision, comments));
    }

    @Operation(summary = "开启新一轮评审（BUG #128 FR-1.4）")
    @PostMapping("/{id}/reviews/new-round")
    @AuditLog(eventType = "REVIEW", entityType = "REQUIREMENT", operation = "开启新评审轮次", entityIdSpel = "#id")
    public Result<Review> startNewReviewRound(
            @PathVariable Long id,
            @RequestParam Long reviewerId,
            @RequestParam(required = false) String reviewerName,
            @RequestParam(required = false) String comments,
            @RequestBody(required = false) List<Long> extraReviewers) {
        return Result.success(requirementService.startNewReviewRound(id, reviewerId, reviewerName, comments, extraReviewers));
    }

    @Operation(summary = "多 reviewer 提交评审（BUG #128 FR-1.4）")
    @PostMapping("/{id}/review-multi")
    @AuditLog(eventType = "REVIEW", entityType = "REQUIREMENT", operation = "发起多 reviewer 评审", entityIdSpel = "#id")
    public Result<Review> submitMultiReviewer(
            @PathVariable Long id,
            @RequestParam Long reviewerId,
            @RequestParam(required = false) String reviewerName,
            @RequestParam(required = false) String comments,
            @RequestBody(required = false) List<Long> extraReviewers) {
        return Result.success(requirementService.submitForReview(id, reviewerId, reviewerName, comments, extraReviewers));
    }

    // ===== v1.42 P1 修复：评审意见列表查询 =====

    @Operation(summary = "查询需求评审意见历史")
    @GetMapping("/{id}/reviews")
    public Result<List<Review>> listReviews(@PathVariable Long id) {
        return Result.success(requirementService.listReviewsByRequirement(id));
    }

    // ===== v1.42 P1 修复：看板拖拽通用状态切换 =====

    @Operation(summary = "通用状态变更（看板拖拽）")
    @PutMapping("/{id}/status")
    public Result<Requirement> changeStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null || status.isBlank()) {
            throw BusinessException.param("status 不能为空");
        }
        return Result.success(requirementService.changeStatus(id, status));
    }
}