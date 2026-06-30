package com.zhutao.medrms.change.controller;

import com.zhutao.medrms.change.domain.entity.ChangeAttachment;
import com.zhutao.medrms.change.domain.entity.ChangeRequest;
import com.zhutao.medrms.change.domain.entity.ChangeTimelineEntry;
import com.zhutao.medrms.change.domain.entity.ImpactAssessment;
import com.zhutao.medrms.change.mapper.ImpactAssessmentMapper;
import com.zhutao.medrms.change.service.ChangeAttachmentService;
import com.zhutao.medrms.change.service.ChangeService;
import com.zhutao.medrms.common.annotation.AuditLog;
import com.zhutao.medrms.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

@Tag(name = "变更管理", description = "变更申请、审批、执行接口")
@RestController
@RequestMapping("/changes")
@RequiredArgsConstructor
public class ChangeController {

    private final ChangeService changeService;
    private final ImpactAssessmentMapper impactAssessmentMapper;
    // v1.43 P1-6 修复：附件服务
    private final ChangeAttachmentService attachmentService;

    @Operation(summary = "创建变更申请")
    @PostMapping
    @AuditLog(eventType = "CREATE", entityType = "CHANGES", operation = "创建变更请求")
    public Result<ChangeRequest> createChangeRequest(@RequestBody CreateChangeRequest req) {
        return Result.success(changeService.createChangeRequest(
                req.getRequirementId(),
                req.getChangeType(),
                req.getReason(),
                req.getUrgency(),
                req.getRequestedBy(),
                req.getTitle()
        ));
    }

    @Operation(summary = "提交变更申请")
    @PostMapping("/{id}/submit")
    @AuditLog(eventType = "STATUS_CHANGE", entityType = "CHANGES", operation = "提交变更", entityIdSpel = "#id")
    public Result<ChangeRequest> submitChange(@PathVariable Long id) {
        return Result.success(changeService.submitChange(id));
    }

    @Operation(summary = "获取变更申请详情")
    @GetMapping("/{id}")
    public Result<ChangeRequest> getChangeById(@PathVariable Long id) {
        return Result.success(changeService.getChangeById(id));
    }

    @Operation(summary = "审批变更申请（v1.47 BUG #115 P0 修复：MAJOR 必须传 signatureId）")
    @PostMapping("/{id}/approve")
    @AuditLog(eventType = "APPROVE", entityType = "CHANGES", operation = "审批变更", entityIdSpel = "#id")
    public Result<ChangeRequest> approveChange(
            @PathVariable Long id,
            @RequestParam Long approverId,
            @RequestParam String decision,
            @RequestParam(required = false) String comments,
            @RequestParam(required = false) Long signatureId) {
        return Result.success(changeService.approveChange(id, approverId, decision, comments, signatureId));
    }

    @Operation(summary = "拒绝变更申请")
    @PostMapping("/{id}/reject")
    public Result<ChangeRequest> rejectChange(@PathVariable Long id, @RequestParam String reason) {
        return Result.success(changeService.rejectChange(id, reason));
    }

    // R92 修复：原 ChangeController 无 PUT 方法（前端调用 PUT /changes/{id} → SY0101）
    // 新增：编辑变更基本信息（标题/描述/原因/紧急程度）。不影响审批流。
    @Operation(summary = "编辑变更（仅基本信息）")
    @PutMapping("/{id}")
    @AuditLog(eventType = "MODIFY", entityType = "CHANGES", operation = "编辑变更", entityIdSpel = "#id")
    public Result<ChangeRequest> updateChange(@PathVariable Long id, @RequestBody ChangeRequest updates) {
        return Result.success(changeService.updateChange(id, updates));
    }

    @Operation(summary = "执行变更")
    @PostMapping("/{id}/execute")
    @AuditLog(eventType = "EXECUTE", entityType = "CHANGES", operation = "执行变更", entityIdSpel = "#id")
    public Result<ChangeRequest> executeChange(@PathVariable Long id, @RequestBody(required = false) Object updatedRequirement) {
        return Result.success(changeService.executeChange(id, null));
    }

    @Operation(summary = "验证变更")
    @PostMapping("/{id}/verify")
    public Result<ChangeRequest> verifyChange(@PathVariable Long id) {
        return Result.success(changeService.verifyChange(id));
    }

    @Operation(summary = "关闭变更")
    @PostMapping("/{id}/close")
    public Result<ChangeRequest> closeChange(@PathVariable Long id) {
        return Result.success(changeService.closeChange(id));
    }

    @Operation(summary = "取消变更（v1.47 BUG #142 P0 修复：补全时间线 - 取消）")
    @PostMapping("/{id}/cancel")
    public Result<ChangeRequest> cancelChange(@PathVariable Long id, @RequestParam String reason) {
        return Result.success(changeService.cancelChange(id, reason));
    }

    @Operation(summary = "EMERGENCY 直执行通道（绕过影响分析）")
    @PostMapping("/{id}/emergency-execute")
    public Result<ChangeRequest> emergencyExecute(@PathVariable Long id,
                                                   @RequestParam Long operatorId,
                                                   @RequestParam String reason) {
        return Result.success(changeService.emergencyDirectExecute(id, operatorId, reason));
    }

    @Operation(summary = "获取需求的所有变更")
    @GetMapping("/requirement/{requirementId}")
    public Result<List<ChangeRequest>> getChangesByRequirement(@PathVariable Long requirementId) {
        return Result.success(changeService.getChangesByRequirement(requirementId));
    }

    @Operation(summary = "获取待审批变更列表")
    @GetMapping("/pending")
    public Result<List<ChangeRequest>> getPendingApprovals() {
        return Result.success(changeService.getPendingApprovals());
    }

    @Operation(summary = "分页查询变更列表")
    @GetMapping("/list")
    public Result<com.zhutao.medrms.common.result.PageResult<ChangeRequest>> listChanges(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String changeType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        // R120 P2 修复：返回 PageResult 含 total，前端分页不再降级
        java.util.List<ChangeRequest> data = changeService.listByConditions(status, changeType, page, size);
        long total = changeService.countByConditions(status, changeType);
        return Result.success(com.zhutao.medrms.common.result.PageResult.of(data, total, page, size));
    }

    @Operation(summary = "获取变更影响评估")
    @GetMapping("/{id}/impacts")
    public Result<List<ImpactAssessment>> getImpactAssessment(@PathVariable Long id) {
        List<ImpactAssessment> assessment = impactAssessmentMapper.selectByChangeId(id);
        return Result.success(assessment);
    }

    @Operation(summary = "执行影响评估")
    @PostMapping("/{id}/assess")
    public Result<ChangeRequest> assessImpact(@PathVariable Long id) {
        return Result.success(changeService.performImpactAssessment(id));
    }

    @Operation(summary = "委派变更审批（FR-1.7）")
    @PostMapping("/{id}/delegate")
    public Result<ChangeRequest> delegateChange(
            @PathVariable Long id,
            @RequestParam Long fromUserId,
            @RequestParam String fromUserName,
            @RequestParam Long toUserId,
            @RequestParam String toUserName) {
        return Result.success(changeService.delegate(id, fromUserId, fromUserName, toUserId, toUserName));
    }

    @Operation(summary = "设置会签人列表（FR-1.7）")
    @PostMapping("/{id}/countersigners")
    public Result<ChangeRequest> setCountersigners(
            @PathVariable Long id,
            @RequestBody List<java.util.Map<String, Object>> signers) {
        return Result.success(changeService.setCountersigners(id, signers));
    }

    @Operation(summary = "会签操作（FR-1.7）")
    @PostMapping("/{id}/countersign")
    public Result<ChangeRequest> countersign(
            @PathVariable Long id,
            @RequestParam Long signerUserId,
            @RequestParam(required = false) String comments) {
        return Result.success(changeService.countersign(id, signerUserId, comments));
    }

    @Operation(summary = "查询变更完整时间线（v1.47 BUG #142 P0 修复）")
    @GetMapping("/{id}/timeline")
    public Result<List<ChangeTimelineEntry>> getTimeline(@PathVariable Long id) {
        return Result.success(changeService.getTimeline(id));
    }

    // ===== v1.43 P1-6 修复：附件上传/下载/删除 =====

    @Operation(summary = "上传附件到变更单")
    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AuditLog(eventType = "CREATE", entityType = "CHANGES", operation = "上传附件", entityIdSpel = "#id")
    public Result<ChangeAttachment> uploadAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "uploaderId", required = false) Long uploaderId,
            @RequestParam(value = "uploaderName", required = false) String uploaderName) {
        return Result.success(attachmentService.upload(id, file, uploaderId, uploaderName));
    }

    @Operation(summary = "查询变更单附件列表")
    @GetMapping("/{id}/attachments")
    public Result<List<ChangeAttachment>> listAttachments(@PathVariable Long id) {
        return Result.success(attachmentService.list(id));
    }

    @Operation(summary = "下载附件")
    @GetMapping("/attachments/{attId}/download")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long attId) {
        ChangeAttachment att = attachmentService.getById(attId);
        File f = new File(att.getStoragePath());
        if (!f.exists()) {
            return ResponseEntity.notFound().build();
        }
        FileSystemResource resource = new FileSystemResource(f);
        MediaType mediaType = (att.getContentType() != null && !att.getContentType().isBlank())
                ? MediaType.parseMediaType(att.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + att.getOriginalName().replace("\"", "_") + "\"")
                .body(resource);
    }

    @Operation(summary = "删除附件")
    @DeleteMapping("/attachments/{attId}")
    @AuditLog(eventType = "DELETE", entityType = "CHANGES", operation = "删除附件", entityIdSpel = "#attId")
    public Result<Void> deleteAttachment(
            @PathVariable Long attId,
            @RequestParam(value = "operatorId", required = false) Long operatorId) {
        attachmentService.delete(attId, operatorId);
        return Result.success();
    }

    @lombok.Data
    public static class CreateChangeRequest {
        private Long requirementId;
        private String changeType;
        private String reason;
        private String urgency;
        private Long requestedBy;
        private String title;
    }
}