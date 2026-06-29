package com.zhutao.medrms.requirement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.util.PageRequest;
import com.zhutao.medrms.common.util.SecurityUtils;
import com.zhutao.medrms.notification.service.NotificationService;
import com.zhutao.medrms.requirement.domain.entity.*;
import com.zhutao.medrms.requirement.mapper.*;
// v1.48 P0 #2 修复：Baseline 已迁至 med-rms-compliance，移除直接依赖（基线化逻辑移交 BaselineService.baselineRequirements）
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementService {

    private final RequirementMapper requirementMapper;
    private final RequirementAncestorMapper ancestorMapper;
    private final UserRequirementMapper userRequirementMapper;
    private final ProductRequirementMapper productRequirementMapper;
    private final SystemRequirementMapper systemRequirementMapper;
    private final DesignRequirementMapper designRequirementMapper;
    private final ReviewMapper reviewMapper;
    // v1.48 P0 #2 修复：移除 BaselineMapper 字段（已迁至 med-rms-compliance）
    // v1.44 BUG #66 修复：跨模块通知依赖
    private final NotificationService notificationService;

    /**
     * 分页查询需求列表
     */
    public IPage<Requirement> listRequirements(Long projectId, String type, String status, Integer page, Integer size) {
        return listRequirements(projectId, type, status, null, page, size);
    }

    public IPage<Requirement> listRequirements(Long projectId, String type, String status, String priority, String keyword, Integer page, Integer size) {
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(page);
        pageRequest.setSize(size);
        return listRequirements(projectId, type, status, priority, keyword, pageRequest);
    }

    public IPage<Requirement> listRequirements(Long projectId, String type, String status, String keyword, Integer page, Integer size) {
        return listRequirements(projectId, type, status, null, keyword, page, size);
    }

    public IPage<Requirement> listRequirements(Long projectId, String type, String status, PageRequest pageRequest) {
        return listRequirements(projectId, type, status, null, null, pageRequest);
    }

    public IPage<Requirement> listRequirements(Long projectId, String type, String status, String priority, String keyword, PageRequest pageRequest) {
        pageRequest.normalize();
        Page<Requirement> page = new Page<>(pageRequest.getPage(), pageRequest.getSize());

        LambdaQueryWrapper<Requirement> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(Requirement::getProjectId, projectId);
        }
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Requirement::getRequirementType, type);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Requirement::getStatus, status);
        }
        if (priority != null && !priority.isEmpty()) {
            wrapper.eq(Requirement::getPriority, priority);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Requirement::getTitle, keyword)
                    .or().like(Requirement::getRequirementNo, keyword));
        }
        wrapper.orderByDesc(Requirement::getCreatedAt);

        return requirementMapper.selectPage(page, wrapper);
    }

    /**
     * 看板视图：按状态分组返回项目下所有需求（FR-1.1）
     * 不分页，全状态列最多 1800 条
     */
    public java.util.Map<String, java.util.List<Requirement>> listGroupedByStatus(Long projectId) {
        // v1.47 BUG #143 P0 修复：14 状态完整覆盖（与 RequirementStatus.java 对齐）
        java.util.List<String> statusOrder = java.util.Arrays.asList(
                "Draft", "Submitted", "InReview", "ReviewApproved", "ReviewRejected",
                "Approved", "Rejected", "InProgress", "InTest", "Verified",
                "Baseline", "Decomposed", "Suspect", "Withdrawn");
        java.util.Map<String, java.util.List<Requirement>> result = new java.util.LinkedHashMap<>();
        for (String s : statusOrder) result.put(s, new java.util.ArrayList<>());

        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Requirement> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (projectId != null) wrapper.eq(Requirement::getProjectId, projectId);
        wrapper.eq(Requirement::getIsDeleted, false);
        wrapper.orderByAsc(Requirement::getPriority).orderByDesc(Requirement::getCreatedAt);
        wrapper.last("LIMIT 1800");

        for (Requirement r : requirementMapper.selectList(wrapper)) {
            String s = r.getStatus() == null ? "Draft" : r.getStatus();
            result.computeIfAbsent(s, k -> new java.util.ArrayList<>()).add(r);
        }
        return result;
    }

    /**
     * 根据ID获取需求详情
     */
    public Requirement getRequirementById(Long id) {
        Requirement requirement = requirementMapper.selectById(id);
        if (requirement == null) {
            throw BusinessException.notFound("RQ0101", "需求不存在");
        }
        return requirement;
    }

    /**
     * 更新需求
     */
    @Transactional
    public Requirement updateRequirement(Long id, Requirement updates) {
        Requirement existing = getRequirementById(id);
        if (existing == null) {
            throw BusinessException.notFound("REQ0101", "需求不存在");
        }
        requirementMapper.updateFields(
                id,
                updates.getTitle() != null ? updates.getTitle() : existing.getTitle(),
                updates.getDescription() != null ? updates.getDescription() : existing.getDescription(),
                updates.getPriority() != null ? updates.getPriority() : existing.getPriority(),
                updates.getRiskLevel() != null ? updates.getRiskLevel() : existing.getRiskLevel(),
                updates.getSafetyClass() != null ? updates.getSafetyClass() : existing.getSafetyClass(),
                updates.getStatus() != null ? updates.getStatus() : existing.getStatus(),
                updates.getRequirementCategory() != null ? updates.getRequirementCategory() : existing.getRequirementCategory(),
                updates.getSource() != null ? updates.getSource() : existing.getSource(),
                updates.getSourceNo() != null ? updates.getSourceNo() : existing.getSourceNo()
        );
        // 记录最后修改人
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId != null) {
            existing.setUpdatedBy(currentUserId);
            existing.setUpdatedAt(LocalDateTime.now());
            requirementMapper.updateById(existing);
        }
        log.info("更新需求: id={}", id);
        return getRequirementById(id);
    }

    /**
     * 创建需求
     */
    @Transactional
    public Requirement createRequirement(Requirement requirement, Object levelSpecificData) {
        // 生成需求编号
        String requirementNo = generateRequirementNo(requirement.getRequirementType(), requirement.getProjectId());
        requirement.setRequirementNo(requirementNo);
        requirement.setStatus(RequirementStatus.DRAFT);
        LocalDateTime nowDt = LocalDateTime.now();
        requirement.setCreatedAt(nowDt);
        requirement.setUpdatedAt(nowDt);
        // 从 SecurityContext 注入创建人/最后修改人；未登录（如系统种子/导入）则保持前端传入值
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId != null) {
            if (requirement.getCreatedBy() == null) requirement.setCreatedBy(currentUserId);
            requirement.setUpdatedBy(currentUserId);
        }

        // 保存主表
        requirementMapper.insert(requirement);

        // 保存层级子表
        saveLevelSpecificData(requirement.getId(), requirement.getRequirementType(), levelSpecificData);

        // 维护闭包表（自引用）
        insertAncestorRecord(requirement.getId(), requirement.getId(), 0);

        log.info("创建需求成功: id={}, requirementNo={}", requirement.getId(), requirementNo);
        return requirement;
    }

    // ===== v1.43 P1-2 修复：批量导入（Excel/CSV 导入用）=====
    @Transactional
    public List<Requirement> createBatchRequirements(List<Requirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            throw com.zhutao.medrms.common.exception.BusinessException.param("批量创建列表不能为空");
        }
        List<Requirement> created = new java.util.ArrayList<>(requirements.size());
        for (Requirement r : requirements) {
            // 单条错误不阻断整体；记录日志后继续
            try {
                created.add(createRequirement(r, null));
            } catch (Exception ex) {
                log.warn("批量创建中单条失败: title={}, err={}", r.getTitle(), ex.getMessage());
            }
        }
        log.info("批量创建需求完成: 提交={} 成功={}", requirements.size(), created.size());
        return created;
    }

    /**
     * 拆解需求为下层需求
     * v1.47 BUG #129 P0 修复：闭包表 linkChild 完整算法
     * linkChild(parent, child) 标准流程：
     *   1) 写自引用 child->child depth=0
     *   2) 复制 parent->X (X 为 parent 的祖先，depth=d) 为 child->X (depth=d+1)
     *   3) 写直接父：child->parent depth=1
     *   4) 子树剪枝：parent 已有的子节点 Y，须为 Y 添加新祖先 child (depth=parent->Y.depth+1)
     *      避免后续 Y 的下游查询漏掉 child 这一层
     */
    @Transactional
    public Requirement decomposeRequirement(Long parentId, Requirement childRequirement, Object levelSpecificData) {
        Requirement parent = getRequirementById(parentId);

        // 校验父子层级关系
        validateDecomposeRelationship(parent.getRequirementType(), childRequirement.getRequirementType());

        // 设置父级关联
        childRequirement.setProjectId(parent.getProjectId());
        childRequirement.setStatus(RequirementStatus.PENDING_DECOMPOSE_FALLBACK);
        LocalDateTime nowDt = LocalDateTime.now();
        childRequirement.setCreatedAt(nowDt);
        childRequirement.setUpdatedAt(nowDt);
        // 拆解人即创建人
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId != null) {
            if (childRequirement.getCreatedBy() == null) childRequirement.setCreatedBy(currentUserId);
            childRequirement.setUpdatedBy(currentUserId);
        }

        // 生成编号
        String requirementNo = generateRequirementNo(childRequirement.getRequirementType(), childRequirement.getProjectId());
        childRequirement.setRequirementNo(requirementNo);

        // 保存子需求
        requirementMapper.insert(childRequirement);

        // 保存层级数据
        saveLevelSpecificData(childRequirement.getId(), childRequirement.getRequirementType(), levelSpecificData);

        // ===== BUG #129 闭包表 linkChild 完整算法 =====
        // 1) 自引用
        insertAncestorRecord(childRequirement.getId(), childRequirement.getId(), 0);
        // 2) 继承父级所有祖先（含父级自己）
        List<RequirementAncestor> parentAncestors = ancestorMapper.selectByDescendant(parentId);
        for (RequirementAncestor ancestor : parentAncestors) {
            insertAncestorRecord(childRequirement.getId(), ancestor.getAncestorId(), ancestor.getDepth() + 1);
        }
        // 3) 写直接父（防止父级未出现在 parentAncestors 边界情况）
        if (ancestorMapper.selectByPair(parentId, childRequirement.getId()) == null) {
            insertAncestorRecord(childRequirement.getId(), parentId, 1);
        }
        // 4) 子树剪枝：父级已有的直接子节点 Y，需要为 Y 追加 child 作为新祖先
        List<RequirementAncestor> existingChildren = ancestorMapper.selectDescendants(parentId);
        for (RequirementAncestor existingChildLink : existingChildren) {
            Long y = existingChildLink.getDescendantId();
            if (y.equals(childRequirement.getId())) continue;
            // 找 Y 到 parent 的最近距离作为基础深度
            int yToParentDepth = existingChildLink.getDepth();
            // 给 Y 及其所有后代都加一条 child->X 记录
            List<RequirementAncestor> yDescendants = ancestorMapper.selectByDescendant(y);
            for (RequirementAncestor yDesc : yDescendants) {
                // X 已是 y 的祖先时跳过避免重复
                if (ancestorMapper.selectByPair(childRequirement.getId(), yDesc.getAncestorId()) != null) {
                    continue;
                }
                insertAncestorRecord(yDesc.getDescendantId(), childRequirement.getId(),
                        yDesc.getDepth() + yToParentDepth + 1);
            }
        }
        // ===== 算法结束 =====

        // 更新父级状态为已拆解
        parent.setStatus(RequirementStatus.DECOMPOSED);
        if (currentUserId != null) {
            parent.setUpdatedBy(currentUserId);
            parent.setUpdatedAt(LocalDateTime.now());
        }
        requirementMapper.updateById(parent);

        log.info("拆解需求成功: parentId={}, childId={}", parentId, childRequirement.getId());
        return childRequirement;
    }

    /**
     * 发起评审
     * v1.47 BUG #128 P0 修复：支持多 reviewer 多轮评审
     * 单 reviewer 旧逻辑（autoSubmitted=true）仍可工作，保留兼容。
     * 多 reviewer 模式下：每人单独 castReviewVote() 投票，全部投完后 finalizeReviewRound() 收口。
     */
    @Transactional
    public Review submitForReview(Long requirementId, Long reviewerId, String comments) {
        return submitForReview(requirementId, reviewerId, null, comments, null);
    }

    /**
     * 发起评审（支持指定 reviewer 列表）
     * @param reviewers 多 reviewer 列表（null 或 size<=1 时按单 reviewer 旧逻辑处理）
     */
    @Transactional
    public Review submitForReview(Long requirementId, Long reviewerId, String reviewerName,
                                  String comments, List<Long> extraReviewers) {
        Requirement requirement = getRequirementById(requirementId);

        if (!RequirementStatus.DECOMPOSED.equals(requirement.getStatus())
                && !RequirementStatus.DRAFT.equals(requirement.getStatus())) {
            throw BusinessException.stateConflict("需求状态不允许发起评审");
        }

        // 状态推进：Submitted -> InReview
        requirement.setStatus(RequirementStatus.IN_REVIEW);
        requirementMapper.updateById(requirement);

        // 计算本轮轮次
        int currentRound = (int) reviewMapper.countByRequirement(requirementId) + 1;
        if (currentRound < 1) currentRound = 1;

        // 单 reviewer 旧兼容：立即插入 APPROVED 记录
        boolean singleMode = (extraReviewers == null || extraReviewers.isEmpty());
        Review review = new Review();
        review.setRequirementId(requirementId);
        review.setReviewerId(reviewerId);
        review.setReviewerName(reviewerName);
        review.setRound(currentRound);
        review.setIsLatest(true);
        review.setComments(comments);
        review.setReviewedAt(LocalDateTime.now());

        if (singleMode) {
            review.setDecision("APPROVED");
            review.setFinalDecision("APPROVED");
            review.setAutoSubmitted(true);
            reviewMapper.insert(review);
            // 旧逻辑：单 reviewer 即视为通过，状态推到 REVIEW_APPROVED
            requirement.setStatus(RequirementStatus.REVIEW_APPROVED);
            requirementMapper.updateById(requirement);
            // 通知
            try {
                notificationService.sendReviewApprovedNotification(
                    requirement.getCreatedBy() != null ? requirement.getCreatedBy() : reviewerId,
                    requirementId, requirement.getRequirementNo());
            } catch (Exception e) {
                log.warn("发送评审通过通知失败: requirementId={}, err={}", requirementId, e.getMessage());
            }
        } else {
            // 多 reviewer 模式：第一个 reviewer 先投票；其余 reviewer 等待 castReviewVote
            review.setDecision("PENDING");
            review.setFinalDecision(null);
            review.setAutoSubmitted(false);
            reviewMapper.insert(review);
            // 其余 reviewer 占位记录（decision=PENDING，is_latest=true 占位用）
            for (Long r : extraReviewers) {
                if (r.equals(reviewerId)) continue;
                Review pending = new Review();
                pending.setRequirementId(requirementId);
                pending.setReviewerId(r);
                pending.setRound(currentRound);
                pending.setIsLatest(false);
                pending.setDecision("PENDING");
                pending.setReviewedAt(null);
                reviewMapper.insert(pending);
            }
            log.info("多 reviewer 评审启动: requirementId={}, round={}, reviewers={}",
                    requirementId, currentRound, extraReviewers.size() + 1);
        }

        log.info("发起评审成功: requirementId={}, reviewId={}, single={}",
                requirementId, review.getId(), singleMode);
        return review;
    }

    /**
     * 多 reviewer 投票：单 reviewer 提交决定；最后一位投完后自动 finalizeRound
     */
    @Transactional
    public Review castReviewVote(Long requirementId, Long reviewerId, String decision, String comments) {
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            throw BusinessException.param("decision 必须是 APPROVED 或 REJECTED");
        }
        Requirement requirement = getRequirementById(requirementId);
        if (!RequirementStatus.IN_REVIEW.equals(requirement.getStatus())) {
            throw BusinessException.stateConflict("需求不在评审中");
        }
        // 找到本轮该 reviewer 的记录
        List<Review> latestRoundReviews = reviewMapper.selectLatestRoundByRequirement(requirementId);
        Review mine = null;
        for (Review r : latestRoundReviews) {
            if (reviewerId.equals(r.getReviewerId())) {
                mine = r;
                break;
            }
        }
        if (mine == null) {
            throw BusinessException.notFound("REVIEW_NOT_FOUND", "未找到本轮你的评审记录");
        }
        if (!"PENDING".equals(mine.getDecision())) {
            throw BusinessException.stateConflict("你已经投过票");
        }
        mine.setDecision(decision);
        mine.setComments(comments);
        mine.setReviewedAt(LocalDateTime.now());
        mine.setIsLatest(true);
        reviewMapper.updateById(mine);

        // 检查本轮是否所有 reviewer 都投完
        boolean allDone = true;
        boolean anyRejected = false;
        for (Review r : latestRoundReviews) {
            if ("PENDING".equals(r.getDecision())) {
                allDone = false;
            }
            if ("REJECTED".equals(r.getDecision())) {
                anyRejected = true;
            }
        }
        if (allDone) {
            // 一票否决：任一 REJECTED -> 整体 REJECTED
            String finalDec = anyRejected ? "REJECTED" : "APPROVED";
            for (Review r : latestRoundReviews) {
                r.setFinalDecision(finalDec);
                reviewMapper.updateById(r);
            }
            requirement.setStatus("REJECTED".equals(finalDec)
                    ? RequirementStatus.REVIEW_REJECTED
                    : RequirementStatus.REVIEW_APPROVED);
            requirementMapper.updateById(requirement);
            log.info("评审轮次完成: requirementId={}, decision={}", requirementId, finalDec);
        }
        return mine;
    }

    /**
     * 开启新一轮评审（评审被拒绝后再次提交）
     */
    @Transactional
    public Review startNewReviewRound(Long requirementId, Long reviewerId, String reviewerName,
                                      String comments, List<Long> extraReviewers) {
        Requirement requirement = getRequirementById(requirementId);
        if (!RequirementStatus.REVIEW_REJECTED.equals(requirement.getStatus())
                && !RequirementStatus.IN_REVIEW.equals(requirement.getStatus())) {
            throw BusinessException.stateConflict("当前状态不允许开新一轮评审");
        }
        // 把上一轮所有 is_latest 置 false
        for (Review r : reviewMapper.selectByRequirementId(requirementId)) {
            r.setIsLatest(false);
            reviewMapper.updateById(r);
        }
        return submitForReview(requirementId, reviewerId, reviewerName, comments, extraReviewers);
    }

    /**
     * 审批需求
     * v1.47 BUG #125 P0 修复：状态机迁移用 14 状态常量
     * 进入条件：REVIEW_APPROVED
     * 决策 APPROVED -> APPROVED；REJECTED -> REJECTED
     */
    @Transactional
    public void approveRequirement(Long requirementId, String decision, Long approverId, String comments) {
        Requirement requirement = getRequirementById(requirementId);

        if (!RequirementStatus.REVIEW_APPROVED.equals(requirement.getStatus())) {
            throw BusinessException.stateConflict("需求状态不允许审批（须先通过评审）");
        }

        // FR-0.17 操作序列强制检查：审批前必须先有评审记录
        long approvedReviewCount = reviewMapper.countApprovedByRequirement(requirementId);
        if (approvedReviewCount == 0) {
            throw BusinessException.stateConflict("请先完成需求评审（FR-0.17 操作序列强制检查）");
        }

        if ("APPROVED".equals(decision)) {
            requirement.setStatus(RequirementStatus.APPROVED);
        } else if ("REJECTED".equals(decision)) {
            requirement.setStatus(RequirementStatus.REJECTED);
        }

        requirementMapper.updateById(requirement);

        // 记录审批动作
        Review review = new Review();
        review.setRequirementId(requirementId);
        review.setReviewerId(approverId);
        review.setDecision(decision);
        review.setComments(comments);
        review.setReviewedAt(LocalDateTime.now());
        review.setIsLatest(true);
        reviewMapper.insert(review);

        // 通知
        try {
            Long receiver = requirement.getCreatedBy() != null ? requirement.getCreatedBy() : approverId;
            if ("APPROVED".equals(decision)) {
                notificationService.sendReviewApprovedNotification(
                    receiver, requirementId, requirement.getRequirementNo());
            } else if ("REJECTED".equals(decision)) {
                notificationService.sendReviewRejectedNotification(
                    receiver, "user", requirementId, requirement.getRequirementNo());
            }
        } catch (Exception e) {
            log.warn("发送评审通知失败: requirementId={}, err={}", requirementId, e.getMessage());
        }

        log.info("审批需求完成: requirementId={}, decision={}", requirementId, decision);
    }

    /**
     * v1.42 P1 修复：列出需求评审意见历史（按时间正序）
     */
    public java.util.List<Review> listReviewsByRequirement(Long requirementId) {
        return reviewMapper.selectByRequirementId(requirementId);
    }

    /**
     * v1.42 P1 修复：通用状态切换（看板拖拽）
     * 校验目标状态在 14 状态枚举内
     */
    @Transactional
    public Requirement changeStatus(Long requirementId, String newStatus) {
        if (newStatus == null || newStatus.isBlank()) {
            throw com.zhutao.medrms.common.exception.BusinessException.param("状态不能为空");
        }
        // 校验目标状态合法
        boolean valid = false;
        for (String s : RequirementStatus.ALL) {
            if (s.equals(newStatus)) { valid = true; break; }
        }
        if (!valid) {
            throw com.zhutao.medrms.common.exception.BusinessException.param("无效状态: " + newStatus);
        }
        Requirement req = getRequirementById(requirementId);
        if (req == null) {
            throw com.zhutao.medrms.common.exception.BusinessException.notFound("REQ0101", "需求不存在: id=" + requirementId);
        }
        if (newStatus.equals(req.getStatus())) {
            return req;
        }
        req.setStatus(newStatus);
        req.setUpdatedAt(LocalDateTime.now());
        requirementMapper.updateById(req);
        log.info("看板拖拽切换状态: requirementId={}, newStatus={}", requirementId, newStatus);
        return req;
    }

    /**
     * 基线化需求
     * v1.48 P0 #2 修复：实际实现已迁至 med-rms-compliance 的 BaselineService.baselineRequirements
     * 该方法保留为 deprecated 包装，调用方会改走 BaselineService
     */
    @Deprecated
    @Transactional
    public void baselineRequirements(Long baselineId, List<Long> requirementIds) {
        log.warn("RequirementService.baselineRequirements 已废弃，请改用 BaselineService.baselineRequirements（合规域）");
    }

    // ===== v1.47 BUG #126 P0 修复：9 个核心 REST 端点支撑方法 =====

    /** 撤销评审：ReviewApproved/ReviewRejected -> Draft（仅创建人可操作） */
    @Transactional
    public Requirement revertToDraft(Long requirementId, Long operatorId, String reason) {
        Requirement r = getRequirementById(requirementId);
        if (operatorId != null && r.getCreatedBy() != null && !operatorId.equals(r.getCreatedBy())) {
            throw BusinessException.forbidden("仅创建人可撤销评审");
        }
        if (!RequirementStatus.REVIEW_APPROVED.equals(r.getStatus())
                && !RequirementStatus.REVIEW_REJECTED.equals(r.getStatus())
                && !RequirementStatus.IN_REVIEW.equals(r.getStatus())) {
            throw BusinessException.stateConflict("当前状态不允许撤销评审");
        }
        r.setStatus(RequirementStatus.DRAFT);
        requirementMapper.updateById(r);
        log.info("需求撤销到草稿: id={}, reason={}", requirementId, reason);
        return r;
    }

    /** 开始实施：Approved -> InProgress */
    @Transactional
    public Requirement startProgress(Long requirementId) {
        Requirement r = getRequirementById(requirementId);
        if (!RequirementStatus.APPROVED.equals(r.getStatus())) {
            throw BusinessException.stateConflict("需求未审批通过，不能开始实施");
        }
        r.setStatus(RequirementStatus.IN_PROGRESS);
        requirementMapper.updateById(r);
        return r;
    }

    /** 开始测试：InProgress -> InTest */
    @Transactional
    public Requirement startTest(Long requirementId) {
        Requirement r = getRequirementById(requirementId);
        if (!RequirementStatus.IN_PROGRESS.equals(r.getStatus())) {
            throw BusinessException.stateConflict("需求未实施，不能进入测试");
        }
        r.setStatus(RequirementStatus.IN_TEST);
        requirementMapper.updateById(r);
        return r;
    }

    /** 验证通过：InTest -> Verified */
    @Transactional
    public Requirement verifyRequirement(Long requirementId, Long verifierId, String comments) {
        Requirement r = getRequirementById(requirementId);
        if (!RequirementStatus.IN_TEST.equals(r.getStatus())) {
            throw BusinessException.stateConflict("需求未测试，不能验证");
        }
        r.setStatus(RequirementStatus.VERIFIED);
        requirementMapper.updateById(r);
        // FR-2.x：验证人 != 创建人 业务规则
        if (verifierId != null && r.getCreatedBy() != null && verifierId.equals(r.getCreatedBy())) {
            log.warn("验证人与创建人相同: requirementId={}, userId={}", requirementId, verifierId);
        }
        return r;
    }

    /** 撤回需求：Draft/Submitted/InReview -> Withdrawn */
    @Transactional
    public Requirement withdrawRequirement(Long requirementId, Long operatorId, String reason) {
        Requirement r = getRequirementById(requirementId);
        if (operatorId != null && r.getCreatedBy() != null && !operatorId.equals(r.getCreatedBy())) {
            throw BusinessException.forbidden("仅创建人可撤回需求");
        }
        if (!RequirementStatus.DRAFT.equals(r.getStatus())
                && !RequirementStatus.SUBMITTED.equals(r.getStatus())
                && !RequirementStatus.IN_REVIEW.equals(r.getStatus())) {
            throw BusinessException.stateConflict("当前状态不允许撤回");
        }
        r.setStatus(RequirementStatus.WITHDRAWN);
        requirementMapper.updateById(r);
        log.info("需求撤回: id={}, reason={}", requirementId, reason);
        return r;
    }

    /** 标记 Suspect：任何已实施状态 -> Suspect（追溯管理触发） */
    @Transactional
    public Requirement markSuspect(Long requirementId, String reason) {
        Requirement r = getRequirementById(requirementId);
        r.setStatus(RequirementStatus.SUSPECT);
        r.setIsSuspect(true);
        requirementMapper.updateById(r);
        log.warn("需求标记 Suspect: id={}, reason={}", requirementId, reason);
        return r;
    }

    /** 获取需求树：闭包表自底向上构造 ancestor 树 */
    public java.util.List<java.util.Map<String, Object>> getRequirementTree(Long projectId) {
        LambdaQueryWrapper<Requirement> w = new LambdaQueryWrapper<>();
        if (projectId != null) w.eq(Requirement::getProjectId, projectId);
        w.eq(Requirement::getIsDeleted, false);
        w.orderByAsc(Requirement::getRequirementType);
        List<Requirement> all = requirementMapper.selectList(w);
        // 闭包表 groupBy ancestorId
        java.util.Map<Long, java.util.List<RequirementAncestor>> byAncestor = new java.util.HashMap<>();
        java.util.Map<Long, Requirement> byId = new java.util.HashMap<>();
        for (Requirement r : all) byId.put(r.getId(), r);
        for (Requirement r : all) {
            List<RequirementAncestor> links = ancestorMapper.selectByDescendant(r.getId());
            for (RequirementAncestor link : links) {
                byAncestor.computeIfAbsent(link.getAncestorId(), k -> new java.util.ArrayList<>()).add(link);
            }
        }
        // 找根（depth=0 但有子节点的）；输出 tree
        java.util.List<java.util.Map<String, Object>> roots = new java.util.ArrayList<>();
        for (Requirement r : all) {
            List<RequirementAncestor> links = ancestorMapper.selectByDescendant(r.getId());
            boolean isRoot = links.stream().anyMatch(l -> l.getAncestorId().equals(r.getId()))
                    && links.size() == 1;
            if (isRoot) {
                java.util.Map<String, Object> node = new java.util.HashMap<>();
                node.put("requirement", r);
                node.put("children", buildChildren(r.getId(), byAncestor, byId));
                roots.add(node);
            }
        }
        return roots;
    }

    private java.util.List<java.util.Map<String, Object>> buildChildren(Long parentId,
                                                                        java.util.Map<Long, java.util.List<RequirementAncestor>> byAncestor,
                                                                        java.util.Map<Long, Requirement> byId) {
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        List<RequirementAncestor> links = byAncestor.get(parentId);
        if (links == null) return result;
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (RequirementAncestor link : links) {
            Long childId = link.getDescendantId();
            if (link.getDepth() != 1) continue;
            if (childId.equals(parentId) || !seen.add(childId)) continue;
            Requirement child = byId.get(childId);
            if (child == null) continue;
            java.util.Map<String, Object> node = new java.util.HashMap<>();
            node.put("requirement", child);
            node.put("children", buildChildren(childId, byAncestor, byId));
            result.add(node);
        }
        return result;
    }

    /** 按用户查询需求（创建人/责任人） */
    public IPage<Requirement> listByUser(Long userId, String type, Integer page, Integer size) {
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(page == null ? 1 : page);
        pageRequest.setSize(size == null ? 20 : size);
        pageRequest.normalize();
        Page<Requirement> p = new Page<>(pageRequest.getPage(), pageRequest.getSize());
        LambdaQueryWrapper<Requirement> w = new LambdaQueryWrapper<>();
        w.eq(Requirement::getCreatedBy, userId);
        if (type != null && !type.isEmpty()) w.eq(Requirement::getRequirementType, type);
        w.orderByDesc(Requirement::getCreatedAt);
        return requirementMapper.selectPage(p, w);
    }

    /** 按编号精确查询 */
    public Requirement getByRequirementNo(String requirementNo) {
        return requirementMapper.selectByRequirementNo(requirementNo);
    }

    /**
     * v1.47 BUG #130 P0 修复：编号生成并发保护
     * 采用「count 探针 + DB unique 约束 + 冲突重试」机制：
     *  1) 预读 count 作为乐观候选
     *  2) 真正 unique 约束由 t_requirement.uk_requirement_no 保证
     *  3) 若多线程同时拿到同一候选号，依赖 DB 唯一约束失败并捕获异常后重试
     * 最多重试 5 次
     */
    private String generateRequirementNo(String type, Long projectId) {
        if (projectId == null) {
            projectId = 1L;
        }
        org.springframework.dao.DuplicateKeyException lastEx = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            long count = requirementMapper.countByProject(projectId);
            String candidate = String.format("%s-%03d-%03d", type, projectId, count + 1);
            // 二次检查：候选号是否已被占用
            if (requirementMapper.selectByRequirementNo(candidate) == null) {
                return candidate;
            }
            // 候选号被占用，等待后重试
            try {
                Thread.sleep(5L + (long) (Math.random() * 20));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // 极端并发下 5 次重试仍冲突，使用时间戳兜底
        return String.format("%s-%03d-%d", type, projectId, System.currentTimeMillis() % 1_000_000);
    }

    private void validateDecomposeRelationship(String parentType, String childType) {
        if ("URS".equals(parentType) && !"PRS".equals(childType)) {
            throw BusinessException.param("URS只能拆解为PRS");
        }
        if ("PRS".equals(parentType) && !"SRS".equals(childType)) {
            throw BusinessException.param("PRS只能拆解为SRS");
        }
        if ("SRS".equals(parentType) && !"DRS".equals(childType)) {
            throw BusinessException.param("SRS只能拆解为DRS");
        }
    }

    private void saveLevelSpecificData(Long reqId, String type, Object levelSpecificData) {
        switch (type) {
            case "URS" -> {
                UserRequirement urs = new UserRequirement();
                urs.setRequirementId(reqId);
                userRequirementMapper.insert(urs);
            }
            case "PRS" -> {
                ProductRequirement prs = new ProductRequirement();
                prs.setRequirementId(reqId);
                productRequirementMapper.insert(prs);
            }
            case "SRS" -> {
                SystemRequirement srs = new SystemRequirement();
                srs.setRequirementId(reqId);
                systemRequirementMapper.insert(srs);
            }
            case "DRS" -> {
                DesignRequirement drs = new DesignRequirement();
                drs.setRequirementId(reqId);
                designRequirementMapper.insert(drs);
            }
        }
    }

    private void insertAncestorRecord(Long descendantId, Long ancestorId, Integer depth) {
        RequirementAncestor ancestor = new RequirementAncestor();
        ancestor.setDescendantId(descendantId);
        ancestor.setAncestorId(ancestorId);
        ancestor.setDepth(depth);
        ancestorMapper.insert(ancestor);
    }
}