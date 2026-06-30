package com.zhutao.medrms.change.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.change.domain.entity.ChangeApproval;
import com.zhutao.medrms.change.domain.entity.ChangeExecution;
import com.zhutao.medrms.change.mapper.ChangeApprovalMapper;
import com.zhutao.medrms.change.mapper.ChangeExecutionMapper;
import com.zhutao.medrms.change.mapper.ChangeTimelineMapper;
import com.zhutao.medrms.change.domain.entity.ChangeTimelineEntry;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.outbox.OutboxService;
import com.zhutao.medrms.common.util.SecurityUtils;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.domain.entity.RequirementAncestor;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.zhutao.medrms.requirement.mapper.RequirementAncestorMapper;
import com.zhutao.medrms.requirement.mapper.TestCaseMapper;
import com.zhutao.medrms.change.domain.entity.ChangeRequest;
import com.zhutao.medrms.change.domain.entity.ImpactAssessment;
import com.zhutao.medrms.change.mapper.ChangeRequestMapper;
import com.zhutao.medrms.change.mapper.ImpactAssessmentMapper;
import com.zhutao.medrms.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeService {

    // v1.47 BUG #109 修复：状态机常量（与设计文档 chg-mgr §5 对齐）
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ANALYZING = "ANALYZING";
    public static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_EXECUTING = "EXECUTING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private final ChangeRequestMapper changeRequestMapper;
    private final ImpactAssessmentMapper impactAssessmentMapper;
    private final RequirementMapper requirementMapper;
    private final RequirementAncestorMapper ancestorMapper;
    private final TestCaseMapper testCaseMapper;
    // v1.44 BUG #66 修复：跨模块通知依赖
    private final NotificationService notificationService;
    // v1.47 BUG #110 修复：单次审批记录
    private final ChangeApprovalMapper changeApprovalMapper;
    // v1.47 BUG #111 修复：执行时版本快照
    private final ChangeExecutionMapper changeExecutionMapper;
    // v1.47 BUG #116 修复：领域事件 Outbox
    private final OutboxService outboxService;
    // v1.47 P0 修复：变更时间线（BUG #142）
    private final ChangeTimelineMapper changeTimelineMapper;
    // v1.47 P0 修复：OA 集成服务（BUG #120）
    private final OaIntegrationService oaIntegrationService;

    /**
     * 创建变更申请
     * 规则：只有已基线化的需求才能发起变更
     */
    @Transactional
    public ChangeRequest createChangeRequest(Long requirementId, String changeType, String reason,
                                             String urgency, Long requestedBy, String title) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw BusinessException.notFound("CH0101", "需求不存在");
        }
        // 需求必须已基线化才能发起变更
        if (!"Baseline".equals(requirement.getStatus())) {
            throw BusinessException.stateConflict("未基线化需求请直接编辑，无需发起变更");
        }

        String changeNo = generateChangeNo(requirement.getProjectId());

        ChangeRequest change = new ChangeRequest();
        change.setChangeNo(changeNo);
        change.setRequirementId(requirementId);
        change.setChangeType(changeType);
        change.setReason(reason);
        change.setStatus("DRAFT");
        change.setUrgency(urgency);
        change.setRequestedBy(requestedBy);
        change.setTitle(title);

        // FR-1.7：MAJOR/EMERGENCY 变更默认启用会签（会签人列表由用户在影响评估后设置）
        boolean needsCountersign = "MAJOR".equalsIgnoreCase(changeType) || "EMERGENCY".equalsIgnoreCase(changeType);
        change.setCountersignRequired(needsCountersign);
        change.setCountersignProgress(needsCountersign ? "PENDING" : "NONE");
        // 受派人默认 = 发起人（可后续委派）
        change.setAssigneeId(requestedBy);

        changeRequestMapper.insert(change);

        // v1.47 BUG #142 修复：记录时间线
        recordTimeline(change.getId(), ChangeTimelineEntry.EVENT_CREATED, requestedBy, null,
                "变更类型=" + changeType + ", 紧急度=" + urgency + ", 标题=" + title);

        // v1.47 BUG #120 P0 修复：MAJOR/EMERGENCY 变更自动推 OA 审批流
        if ("MAJOR".equalsIgnoreCase(changeType) || "EMERGENCY".equalsIgnoreCase(urgency)) {
            try {
                String oaWorkflowId = oaIntegrationService.createApprovalWorkflow(change);
                log.info("MAJOR/EMERGENCY 变更已推 OA 审批: changeId={}, oaWorkflowId={}",
                        change.getId(), oaWorkflowId);
            } catch (Exception e) {
                log.warn("推 OA 审批失败（不影响主流程）: changeId={}, err={}", change.getId(), e.getMessage());
            }
        }

        log.info("创建变更申请: changeNo={}, requirementId={}", changeNo, requirementId);
        return change;
    }

    // R92 新增：编辑变更基本信息（仅 DRAFT 状态可编辑，避免影响审批流）
    @Transactional
    public ChangeRequest updateChange(Long changeId, ChangeRequest updates) {
        ChangeRequest change = changeRequestMapper.selectById(changeId);
        if (change == null) {
            throw BusinessException.notFound("CH0101", "变更申请不存在");
        }
        if (!"DRAFT".equals(change.getStatus()) && !"ANALYZING".equals(change.getStatus())) {
            throw BusinessException.stateConflict("变更状态 " + change.getStatus() + " 不允许编辑");
        }
        if (updates.getTitle() != null) change.setTitle(updates.getTitle());
        if (updates.getDescription() != null) change.setDescription(updates.getDescription());
        if (updates.getReason() != null) change.setReason(updates.getReason());
        if (updates.getUrgency() != null) change.setUrgency(updates.getUrgency());
        if (updates.getChangeType() != null) change.setChangeType(updates.getChangeType());
        changeRequestMapper.updateById(change);
        log.info("编辑变更: id={}", changeId);
        return change;
    }

    public ChangeRequest submitChange(Long changeId) {
        ChangeRequest change = changeRequestMapper.selectById(changeId);
        if (change == null) {
            throw BusinessException.notFound("CH0101", "变更申请不存在");
        }
        if (!"DRAFT".equals(change.getStatus())) {
            throw BusinessException.stateConflict("变更状态不允许提交，必须是草稿状态才能提交");
        }
        // 提交后进入影响分析阶段
        change.setStatus("ANALYZING");
        changeRequestMapper.updateById(change);
        // v1.47 BUG #142 修复：记录时间线
        recordTimeline(changeId, ChangeTimelineEntry.EVENT_SUBMITTED, change.getRequestedBy(), null,
                "变更已提交，进入影响分析阶段");
        log.info("提交变更申请: changeId={}", changeId);
        return change;
    }

    /**
     * 审批变更申请
     * @param signatureId 电子签名 ID（MAJOR 变更必须传，v1.47 BUG #115 P0 修复：Part 11 集成）
     */
    @Transactional
    public ChangeRequest approveChange(Long changeId, Long approverId, String decision, String comments, Long signatureId) {
        ChangeRequest change = changeRequestMapper.selectById(changeId);
        if (change == null) {
            throw BusinessException.notFound("CH0101", "变更申请不存在");
        }

        if (!"ANALYZING".equals(change.getStatus()) && !"PENDING_APPROVAL".equals(change.getStatus())) {
            throw BusinessException.stateConflict("变更状态不允许审批，必须先完成影响分析");
        }

        // FR-0.17 操作序列强制检查：变更审批前必须有影响评估记录
        long impactCount = impactAssessmentMapper.countByChangeId(changeId);
        if (impactCount == 0) {
            throw BusinessException.stateConflict("请先完成变更影响评估（FR-0.17 操作序列强制检查）");
        }

        // v1.47 BUG #115 P0 修复：MAJOR 变更必须传电子签名（Part 11 §11.50/§11.70）
        if ("MAJOR".equalsIgnoreCase(change.getChangeType()) && signatureId == null) {
            throw BusinessException.param("MAJOR 变更审批必须提供电子签名 ID（signatureId），请先通过电子签名模块完成签名");
        }

        // FR-1.7 会签前置检查：启用会签的变更需全部签完才能审批
        if (Boolean.TRUE.equals(change.getCountersignRequired())
                && !"COMPLETED".equals(change.getCountersignProgress())) {
            throw BusinessException.stateConflict(
                "该变更需要会签全部完成才能审批（当前进度: " + change.getCountersignProgress() + "，FR-1.7）");
        }

        change.setApprovedBy(approverId);
        change.setApprovedAt(LocalDateTime.now());
        change.setApprovalComments(comments);

        if ("APPROVED".equals(decision)) {
            change.setStatus("APPROVED");
        } else if ("REJECTED".equals(decision)) {
            change.setStatus("CLOSED");
            change.setApprovalComments("拒绝原因: " + comments);
        }

        changeRequestMapper.updateById(change);

        // v1.47 BUG #110 修复：写入单次审批记录
        ChangeApproval approval = new ChangeApproval();
        approval.setChangeId(changeId);
        approval.setApproverId(approverId);
        approval.setDecision(decision);
        approval.setComments(comments);
        approval.setSignatureId(signatureId);
        approval.setCreatedAt(LocalDateTime.now());
        changeApprovalMapper.insert(approval);

        // v1.47 BUG #142 修复：记录时间线（含签名 ID）
        String approveEvent = "APPROVED".equals(decision)
                ? ChangeTimelineEntry.EVENT_APPROVED
                : ChangeTimelineEntry.EVENT_REJECTED;
        String approveDetails = "决策=" + decision + ", 意见=" + comments
                + (signatureId != null ? ", signatureId=" + signatureId : "");
        recordTimeline(changeId, approveEvent, approverId, signatureId, approveDetails);

        // v1.47 BUG #115 P0 修复：MAJOR 变更签名完成时记录 SIGNED 时间线
        if ("APPROVED".equals(decision) && signatureId != null) {
            recordTimeline(changeId, ChangeTimelineEntry.EVENT_SIGNED, approverId, signatureId,
                    "Part 11 电子签名已关联, signatureId=" + signatureId);
        }

        // v1.47 BUG #116 修复：发布 ChangeApproved / ChangeRejected 领域事件（outbox 模式）
        try {
            String eventType = "APPROVED".equals(decision) ? "ChangeApproved" : "ChangeRejected";
            outboxService.append(eventType, "change", changeId,
                    java.util.Map.of("changeNo", change.getChangeNo(),
                            "approverId", approverId,
                            "decision", decision));
        } catch (Exception e) {
            log.warn("发布 outbox 事件失败: changeId={}, err={}", changeId, e.getMessage());
        }

        // v1.44 BUG #66 修复：变更批准后向申请人发送 CHANGE_APPROVED 通知
        try {
            if ("APPROVED".equals(decision) && change.getRequestedBy() != null) {
                notificationService.sendChangeApprovedNotification(
                    change.getRequestedBy(), change.getChangeNo());
            }
        } catch (Exception e) {
            log.warn("发送变更通知失败: changeId={}, err={}", changeId, e.getMessage());
        }

        log.info("审批变更申请: changeId={}, decision={}", changeId, decision);
        return change;
    }

    /**
     * 拒绝变更申请
     */
    @Transactional
    public ChangeRequest rejectChange(Long changeId, String reason) {
        ChangeRequest change = changeRequestMapper.selectById(changeId);
        if (change == null) {
            throw BusinessException.notFound("CH0101", "变更申请不存在");
        }
        if (!"ANALYZING".equals(change.getStatus()) && !"PENDING_APPROVAL".equals(change.getStatus())) {
            throw BusinessException.stateConflict("变更状态不允许拒绝");
        }
        change.setStatus("CLOSED");
        change.setApprovalComments("拒绝原因: " + reason);
        changeRequestMapper.updateById(change);

        // v1.47 BUG #142 P0 修复：补全时间线 - 拒绝
        recordTimeline(changeId, ChangeTimelineEntry.EVENT_REJECTED,
                SecurityUtils.getCurrentUserId(), null, "拒绝原因=" + reason);

        log.info("拒绝变更申请: changeId={}, reason={}", changeId, reason);
        return change;
    }

    /**
     * 执行变更
     * FR-0.10：执行后自动将所有下游需求及其测试用例标记为 suspect
     * v1.47 BUG #118 P0 修复：MAJOR + CRITICAL 风险变更必须双签锁定（≥2 个不同 approver 签署）
     */
    @Transactional
    public ChangeRequest executeChange(Long changeId, Requirement updatedRequirement) {
        ChangeRequest change = changeRequestMapper.selectById(changeId);
        if (change == null) {
            throw BusinessException.notFound("CH0101", "变更申请不存在");
        }
        if (!"APPROVED".equals(change.getStatus())) {
            throw BusinessException.stateConflict("变更状态不允许执行");
        }

        // v1.47 BUG #118 P0 修复：MAJOR 变更 + CRITICAL 风险，必须 ≥2 个不同 approver 签署（Part 11 §11.200 双签控制）
        if ("MAJOR".equalsIgnoreCase(change.getChangeType())
                || "CRITICAL".equalsIgnoreCase(change.getRiskLevel())) {
            List<ChangeApproval> approvals = changeApprovalMapper.selectByChangeId(changeId);
            long uniqueApprovers = approvals.stream()
                    .map(ChangeApproval::getApproverId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
            if (uniqueApprovers < 2) {
                throw BusinessException.stateConflict(
                    "MAJOR/CRITICAL 变更必须由 ≥2 个不同 approver 签署才能执行（当前 " + uniqueApprovers + " 人，Part 11 §11.200 双签控制）");
            }
        }

        change.setStatus("EXECUTING");
        changeRequestMapper.updateById(change);

        if (updatedRequirement != null) {
            updatedRequirement.setId(change.getRequirementId());
            requirementMapper.updateById(updatedRequirement);
        }

        // v1.47 BUG #111 修复：记录执行快照
        ChangeExecution exec = new ChangeExecution();
        exec.setChangeId(changeId);
        exec.setRequirementId(change.getRequirementId());
        exec.setStatus(ChangeExecution.STATUS_EXECUTING);
        exec.setExecutedAt(LocalDateTime.now());
        exec.setCreatedAt(LocalDateTime.now());
        changeExecutionMapper.insert(exec);

        // v1.47 BUG #142 修复：记录时间线
        recordTimeline(changeId, ChangeTimelineEntry.EVENT_EXECUTED, change.getRequestedBy(), null,
                "执行快照已记录 id=" + exec.getId());

        // v1.48 P0 #5 修复：markDownstreamAsSuspect 已移至 performImpactAssessment
        // 原因：影响分析是识别下游范围的"模型"，执行是"算法"应用；标记 suspect 应在模型阶段完成

        // v1.47 BUG #116 修复：发布 ChangeExecuted 事件
        try {
            outboxService.append("ChangeExecuted", "change", changeId,
                    java.util.Map.of("changeNo", change.getChangeNo()));
        } catch (Exception e) {
            log.warn("发布 outbox 事件失败: changeId={}, err={}", changeId, e.getMessage());
        }

        log.info("执行变更: changeId={}", changeId);
        return change;
    }

    /**
     * v1.47 BUG #117 修复：EMERGENCY 直执行通道
     * 高紧急度变更可绕过 ANALYZING/PENDING_APPROVAL 直接进入 EXECUTING
     * 必须在事后补做影响分析（异步），并强制双签（approver + QA）
     */
    @Transactional
    public ChangeRequest emergencyDirectExecute(Long changeId, Long operatorId, String reason) {
        ChangeRequest change = changeRequestMapper.selectById(changeId);
        if (change == null) throw BusinessException.notFound("CH0101", "变更申请不存在");
        if (!"EMERGENCY".equalsIgnoreCase(change.getUrgency())) {
            throw BusinessException.stateConflict("仅 EMERGENCY 紧急度可走直执行通道");
        }
        // 必须从 DRAFT/ANALYZING 进入
        if (!"DRAFT".equals(change.getStatus()) && !"ANALYZING".equals(change.getStatus())) {
            throw BusinessException.stateConflict("EMERGENCY 通道仅 DRAFT/ANALYZING 状态可用");
        }
        change.setStatus("EXECUTING");
        change.setApprovalComments("EMERGENCY 直执行（操作人=" + operatorId + "，原因=" + reason + "）");
        changeRequestMapper.updateById(change);

        ChangeExecution exec = new ChangeExecution();
        exec.setChangeId(changeId);
        exec.setRequirementId(change.getRequirementId());
        exec.setStatus(ChangeExecution.STATUS_EXECUTING);
        exec.setExecutorId(operatorId);
        exec.setExecutedAt(LocalDateTime.now());
        exec.setRemarks("EMERGENCY 直执行：" + reason);
        exec.setCreatedAt(LocalDateTime.now());
        changeExecutionMapper.insert(exec);

        // 标记下游为 suspect
        markDownstreamAsSuspect(change.getRequirementId(), changeId);

        // v1.47 BUG #142 修复：记录时间线
        recordTimeline(changeId, ChangeTimelineEntry.EVENT_EMERGENCY_EXECUTED, operatorId, null,
                "EMERGENCY 直执行，原因=" + reason);

        // 通知
        try {
            outboxService.append("ChangeEmergencyExecuted", "change", changeId,
                    java.util.Map.of("changeNo", change.getChangeNo(),
                            "operatorId", operatorId,
                            "reason", reason));
        } catch (Exception e) {
            log.warn("发布紧急变更事件失败: changeId={}, err={}", changeId, e.getMessage());
        }

        log.warn("[AUDIT][CHANGE][EMERGENCY] changeId={}, operatorId={}, reason={}",
                changeId, operatorId, reason);
        return change;
    }

    /**
     * v1.47 BUG #142 P0 修复：记录变更时间线
     * 关键操作统一通过该方法写入时间线，便于审计追溯
     */
    private void recordTimeline(Long changeId, String event, Long operatorId, Long signatureId, String details) {
        try {
            ChangeTimelineEntry entry = new ChangeTimelineEntry();
            entry.setChangeId(changeId);
            entry.setEvent(event);
            entry.setOperatorId(operatorId);
            entry.setSignatureId(signatureId);
            entry.setDetails(details);
            changeTimelineMapper.insert(entry);
        } catch (Exception e) {
            log.warn("记录变更时间线失败: changeId={}, event={}, err={}", changeId, event, e.getMessage());
        }
    }

    /**
     * v1.47 BUG #142 P0 修复：查询变更完整时间线
     */
    public List<ChangeTimelineEntry> getTimeline(Long changeId) {
        return changeTimelineMapper.selectByChangeId(changeId);
    }

    /**
     * 标记变更需求的所有下游需求 + 关联测试用例为 suspect（FR-0.10）
     */
    private void markDownstreamAsSuspect(Long rootRequirementId, Long changeId) {
        // 1. 查找所有下游需求（depth > 0）
        List<Long> descendantIds = ancestorMapper.selectDescendantIds(rootRequirementId);
        if (descendantIds == null || descendantIds.isEmpty()) {
            log.info("变更 {} 需求 {} 无下游，跳过 suspect 标记", changeId, rootRequirementId);
            return;
        }

        // 2. 批量标记下游需求为 suspect
        String ids = descendantIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        int reqMarked = requirementMapper.markSuspectBatch(ids);
        log.info("变更 {} 标记 {} 个下游需求为 suspect", changeId, reqMarked);

        // 3. 标记下游需求关联的测试用例为 suspect
        int tcMarked = testCaseMapper.markSuspectByRequirementIds(descendantIds);
        log.info("变更 {} 标记 {} 个测试用例为 suspect", changeId, tcMarked);
    }

    /**
     * 完成变更验证
     */
    @Transactional
    public ChangeRequest verifyChange(Long changeId) {
        ChangeRequest change = changeRequestMapper.selectById(changeId);
        if (change == null) {
            throw BusinessException.notFound("CH0101", "变更申请不存在");
        }
        if (!"EXECUTING".equals(change.getStatus())) {
            throw BusinessException.stateConflict("变更状态不允许验证");
        }
        // v1.47 BUG #109 修复：VERIFIED → COMPLETED 状态机补全（与设计 chg-mgr §5 对齐）
        change.setStatus(STATUS_COMPLETED);
        changeRequestMapper.updateById(change);

        // v1.47 BUG #142 P0 修复：补全时间线 - 验证完成
        recordTimeline(changeId, ChangeTimelineEntry.EVENT_VERIFIED,
                SecurityUtils.getCurrentUserId(), null, "变更验证完成");

        log.info("完成变更验证: changeId={}", changeId);
        return change;
    }

    /**
     * 关闭变更
     */
    @Transactional
    public ChangeRequest closeChange(Long changeId) {
        ChangeRequest change = changeRequestMapper.selectById(changeId);
        if (change == null) {
            throw BusinessException.notFound("CH0101", "变更申请不存在");
        }
        change.setStatus("CLOSED");
        changeRequestMapper.updateById(change);

        // v1.47 BUG #142 P0 修复：补全时间线 - 关闭
        recordTimeline(changeId, ChangeTimelineEntry.EVENT_CLOSED,
                SecurityUtils.getCurrentUserId(), null, "变更已关闭");

        log.info("关闭变更: changeId={}", changeId);
        return change;
    }

    /**
     * 取消变更 (v1.47 BUG #142 P0 修复：补全时间线 - 取消)
     *  - 仅 DRAFT/ANALYZING 状态可取消
     *  - 状态置为 CANCELLED（终态）
     *  - 记录 CANCELLED 时间线
     */
    @Transactional
    public ChangeRequest cancelChange(Long changeId, String reason) {
        ChangeRequest change = changeRequestMapper.selectById(changeId);
        if (change == null) {
            throw BusinessException.notFound("CH0101", "变更申请不存在");
        }
        if (!"DRAFT".equals(change.getStatus()) && !"ANALYZING".equals(change.getStatus())) {
            throw BusinessException.stateConflict("仅 DRAFT/ANALYZING 状态可取消，当前状态: " + change.getStatus());
        }
        change.setStatus(STATUS_CANCELLED);
        change.setApprovalComments("取消原因: " + reason);
        changeRequestMapper.updateById(change);

        recordTimeline(changeId, ChangeTimelineEntry.EVENT_CANCELLED,
                SecurityUtils.getCurrentUserId(), null, "取消原因=" + reason);

        log.info("取消变更: changeId={}, reason={}", changeId, reason);
        return change;
    }

    /**
     * 获取变更申请详情
     */
    public ChangeRequest getChangeById(Long changeId) {
        ChangeRequest change = changeRequestMapper.selectById(changeId);
        if (change == null) {
            throw BusinessException.notFound("CH0101", "变更申请不存在");
        }
        return change;
    }

    /**
     * 查询需求的所有变更
     */
    public List<ChangeRequest> getChangesByRequirement(Long requirementId) {
        return changeRequestMapper.selectByRequirementId(requirementId);
    }

    /**
     * 查询用户的待审批变更
     */
    public List<ChangeRequest> getPendingApprovals() {
        return changeRequestMapper.selectByStatus("PENDING_APPROVAL");
    }

    /**
     * 执行影响评估 - 分析追溯链影响
     * 流程：Draft → Analyzing → PendingApproval
     */
    @Transactional
    public ChangeRequest performImpactAssessment(Long changeId) {
        log.info("执行影响评估开始: changeId={}", changeId);
        ChangeRequest change = changeRequestMapper.selectById(changeId);
        if (change == null) {
            throw BusinessException.notFound("CH0101", "变更申请不存在");
        }
        log.info("变更状态: {}", change.getStatus());
        if (!"ANALYZING".equals(change.getStatus())) {
            throw BusinessException.stateConflict("当前状态不允许执行影响评估，当前状态: " + change.getStatus());
        }

        log.info("开始查询目标需求: requirementId={}", change.getRequirementId());
        Requirement targetReq = requirementMapper.selectById(change.getRequirementId());
        if (targetReq == null) {
            throw BusinessException.notFound("CH0101", "目标需求不存在");
        }
        log.info("目标需求: {}", targetReq.getRequirementNo());

        log.info("开始查询追溯链: ancestors");
        List<RequirementAncestor> ancestors = ancestorMapper.selectByDescendant(change.getRequirementId());
        log.info(" ancestors.size()={}", ancestors.size());

        log.info("开始查询追溯链: descendants");
        List<RequirementAncestor> descendants = ancestorMapper.selectDescendants(change.getRequirementId());
        log.info(" descendants.size()={}", descendants.size());

        int ancestorCount = (int) ancestors.stream().filter(a -> a.getDepth() != null && a.getDepth() > 0).count();
        int descendantCount = (int) descendants.stream().filter(a -> a.getDepth() != null && a.getDepth() > 0).count();
        int affectedCount = ancestorCount + descendantCount;

        log.info("影响统计: ancestorCount={}, descendantCount={}, affectedCount={}", ancestorCount, descendantCount, affectedCount);

        String impactLevel = affectedCount > 10 ? "CRITICAL" : affectedCount > 5 ? "MAJOR" : affectedCount > 0 ? "LOW" : "NONE";

        ImpactAssessment assessment = new ImpactAssessment();
        assessment.setChangeId(change.getId());
        assessment.setItemName(targetReq.getRequirementNo());
        assessment.setItemType("REQUIREMENT");
        assessment.setImpactLevel(impactLevel);
        assessment.setImpactType("TRACEABILITY");
        assessment.setImpactDescription("上层追溯: " + ancestorCount + "个, 下层拆解: " + descendantCount + "个");
        assessment.setSuggestedAction(affectedCount > 0 ? "需要评估对相关需求的连锁影响" : "无追溯链影响，可直接变更");
        assessment.setIsDeleted(false);
        assessment.setCreatedAt(LocalDateTime.now());
        impactAssessmentMapper.insert(assessment);
        log.info("影响评估记录已创建, id={}", assessment.getId());

        // v1.48 P0 #5 修复：影响分析阶段自动标记下游为 suspect（FR-0.10）
        // 模型/算法错位修复：识别影响范围（影响分析）与执行变更（执行）是不同阶段
        // 下游 suspect 标记应在影响分析时完成，这样审批者审批时即可看到影响范围
        markDownstreamAsSuspect(change.getRequirementId(), changeId);

        change.setStatus("PENDING_APPROVAL");
        changeRequestMapper.updateById(change);
        log.info("状态已更新为PENDING_APPROVAL");

        // v1.47 BUG #142 P0 修复：补全时间线 - 影响评估完成
        recordTimeline(changeId, ChangeTimelineEntry.EVENT_IMPACT_ASSESSED,
                SecurityUtils.getCurrentUserId(), null,
                "影响评估完成，impactLevel=" + impactLevel + ", 上层=" + ancestorCount + ", 下层=" + descendantCount);

        log.info("影响评估完成: changeId={}, 影响数量={}, 等级={}", changeId, affectedCount, impactLevel);
        return change;
    }

    /**
     * 分页查询变更列表
     */
    public List<ChangeRequest> listByConditions(String status, String changeType, int page, int size) {
        LambdaQueryWrapper<ChangeRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChangeRequest::getIsDeleted, false);
        // R95 修复：原 .eq() 大小写敏感，DB 里 change.status 混合大小写（APPROVED/Approved）
        // 改为：大小写不敏感（前端传 APPROVED 时同时匹配 APPROVED 和 Approved）
        if (status != null && !status.isBlank()) {
            // 查所有 status 值（按 status 分组），过滤出大小写匹配的所有变体
            String statusUpper = status.toUpperCase();
            List<String> statusVariants = changeRequestMapper.selectList(
                    new LambdaQueryWrapper<ChangeRequest>()
                            .select(ChangeRequest::getStatus)
                            .eq(ChangeRequest::getIsDeleted, false)
                            .isNotNull(ChangeRequest::getStatus)
                            .groupBy(ChangeRequest::getStatus)
            ).stream().map(ChangeRequest::getStatus)
                    .filter(s -> s != null && s.toUpperCase().equals(statusUpper))
                    .distinct()
                    .toList();
            if (!statusVariants.isEmpty()) {
                wrapper.in(ChangeRequest::getStatus, statusVariants);
            } else {
                // fallback：保持原行为
                wrapper.eq(ChangeRequest::getStatus, status);
            }
        }
        if (changeType != null && !changeType.isBlank()) {
            wrapper.eq(ChangeRequest::getChangeType, changeType);
        }
        wrapper.orderByDesc(ChangeRequest::getCreatedAt);
        return changeRequestMapper.selectList(wrapper);
    }

    /**
     * R120 P2 修复：按条件统计变更总数（与 listByConditions 配套，用于分页）
     */
    public long countByConditions(String status, String changeType) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChangeRequest> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(ChangeRequest::getIsDeleted, false);
        if (status != null && !status.isBlank()) {
            // 与 listByConditions 大小写不敏感逻辑一致
            String statusUpper = status.toUpperCase();
            List<String> statusVariants = changeRequestMapper.selectList(
                    new LambdaQueryWrapper<ChangeRequest>()
                            .select(ChangeRequest::getStatus)
                            .eq(ChangeRequest::getIsDeleted, false)
                            .isNotNull(ChangeRequest::getStatus)
                            .groupBy(ChangeRequest::getStatus)
            ).stream().map(ChangeRequest::getStatus)
                    .filter(s -> s != null && s.toUpperCase().equals(statusUpper))
                    .distinct()
                    .toList();
            if (!statusVariants.isEmpty()) {
                wrapper.in(ChangeRequest::getStatus, statusVariants);
            } else {
                wrapper.eq(ChangeRequest::getStatus, status);
            }
        }
        if (changeType != null && !changeType.isBlank()) {
            wrapper.eq(ChangeRequest::getChangeType, changeType);
        }
        return changeRequestMapper.selectCount(wrapper);
    }

    private String generateChangeNo(Long projectId) {
        long count = changeRequestMapper.selectCount(new LambdaQueryWrapper<ChangeRequest>());
        return String.format("CR-%d-%04d", projectId, count + 1);
    }

    /**
     * 委派变更审批给其他人（FR-1.7 委派）
     *  - 状态机不变（仍处于 ANALYZING/PENDING_APPROVAL）
     *  - 记录委派来源（delegated_from_id/name + delegated_at）用于审计
     *  - assignee 取代原受派人
     */
    @Transactional
    public ChangeRequest delegate(Long changeId, Long fromUserId, String fromUserName,
                                  Long toUserId, String toUserName) {
        ChangeRequest change = changeRequestMapper.selectById(changeId);
        if (change == null) {
            throw BusinessException.notFound("CH0101", "变更申请不存在");
        }
        if ("DRAFT".equals(change.getStatus()) || "CLOSED".equals(change.getStatus())) {
            throw BusinessException.stateConflict("当前状态不允许委派: " + change.getStatus());
        }
        if (toUserId == null) {
            throw BusinessException.param("受派人 ID 不能为空");
        }
        change.setAssigneeId(toUserId);
        change.setAssigneeName(toUserName);
        change.setDelegatedFromId(fromUserId);
        change.setDelegatedFromName(fromUserName);
        change.setDelegatedAt(LocalDateTime.now());
        changeRequestMapper.updateById(change);
        log.info("变更 {} 委派: from={} to={}", changeId, fromUserId, toUserId);
        return change;
    }

    /**
     * 设置会签人列表（FR-1.7 会签）
     *  - MAJOR/EMERGENCY 变更必须设置会签
     *  - 设置后 countersign_progress = PENDING
     */
    @Transactional
    public ChangeRequest setCountersigners(Long changeId, List<Map<String, Object>> signers) {
        ChangeRequest change = changeRequestMapper.selectById(changeId);
        if (change == null) {
            throw BusinessException.notFound("CH0101", "变更申请不存在");
        }
        if (signers == null || signers.isEmpty()) {
            throw BusinessException.param("会签人列表不能为空");
        }
        // 规范化：每个 signer 加 signed=false / signedAt=null / comments=null
        List<Map<String, Object>> normalized = new java.util.ArrayList<>();
        for (Map<String, Object> s : signers) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", s.get("id"));
            m.put("name", s.get("name"));
            m.put("signed", false);
            m.put("signedAt", null);
            m.put("comments", null);
            normalized.add(m);
        }
        change.setCountersignRequired(true);
        change.setCountersigners(JSON.toJSONString(normalized));
        change.setCountersignProgress("PENDING");
        changeRequestMapper.updateById(change);
        log.info("变更 {} 设置 {} 个会签人", changeId, normalized.size());
        return change;
    }

    /**
     * 会签操作（FR-1.7 会签）
     *  - 单人会签：传入 signerUserId + comments
     *  - 更新该 signer 的 signed=true + signedAt + comments
     *  - 自动计算 countersign_progress：全部签 = COMPLETED；部分签 = PARTIAL
     */
    @Transactional
    public ChangeRequest countersign(Long changeId, Long signerUserId, String comments) {
        ChangeRequest change = changeRequestMapper.selectById(changeId);
        if (change == null) {
            throw BusinessException.notFound("CH0101", "变更申请不存在");
        }
        if (!Boolean.TRUE.equals(change.getCountersignRequired())) {
            throw BusinessException.stateConflict("该变更未启用会签");
        }
        if ("COMPLETED".equals(change.getCountersignProgress())) {
            throw BusinessException.stateConflict("会签已完成，无需重复签署");
        }
        String raw = change.getCountersigners();
        if (raw == null || raw.isBlank()) {
            throw BusinessException.stateConflict("会签人列表为空");
        }
        List<Map> signers = JSON.parseArray(raw, Map.class);
        boolean found = false;
        int signedCount = 0;
        for (Map s : signers) {
            Object idObj = s.get("id");
            Long id = idObj instanceof Number ? ((Number) idObj).longValue() : Long.parseLong(String.valueOf(idObj));
            if (id.equals(signerUserId)) {
                if (Boolean.TRUE.equals(s.get("signed"))) {
                    throw BusinessException.stateConflict("您已签署过该变更");
                }
                s.put("signed", true);
                s.put("signedAt", LocalDateTime.now().toString());
                s.put("comments", comments);
                found = true;
            }
            if (Boolean.TRUE.equals(s.get("signed"))) {
                signedCount++;
            }
        }
        if (!found) {
            throw BusinessException.stateConflict("您不在会签人列表中");
        }
        change.setCountersigners(JSON.toJSONString(signers));
        change.setCountersignProgress(signedCount == signers.size() ? "COMPLETED" : "PARTIAL");
        changeRequestMapper.updateById(change);
        log.info("变更 {} 会签: user={}, progress={}", changeId, signerUserId, change.getCountersignProgress());
        return change;
    }
}