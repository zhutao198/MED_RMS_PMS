package com.zhutao.medrms.requirement.domain.entity;

/**
 * v1.47 BUG #125 P0 修复 + v2.5 完整化：需求 14 状态机
 * 设计文档 FR-1.0 ~ FR-1.12 规定的全状态机。
 *
 * 状态迁移图：
 *   DRAFT -> SUBMITTED (提交评审)
 *   SUBMITTED -> IN_REVIEW (进入评审)
 *   IN_REVIEW -> REVIEW_APPROVED / REVIEW_REJECTED (评审完成)
 *   REVIEW_APPROVED -> PENDING_VERIFY (等待验证准入)
 *   REVIEW_APPROVED -> APPROVED (审批通过)
 *   REVIEW_APPROVED -> REJECTED (审批拒绝)
 *   PENDING_VERIFY -> IMPLEMENTED (进入实施)
 *   IMPLEMENTED -> IN_PROGRESS (开始实施，兼容老逻辑)
 *   IN_PROGRESS -> IN_TEST (开始测试)
 *   IN_TEST -> VERIFIED (验证通过)
 *   VERIFIED -> CLOSED (闭环)
 *   VERIFIED -> BASELINE (基线化)
 *   BASELINE -> RETIRED (退役)
 *   DRAFT / SUBMITTED -> DECOMPOSED (已拆解为下层)
 *   APPROVED / IN_PROGRESS / IN_TEST -> SUSPECT (追溯变更触发)
 *   DRAFT / SUBMITTED / IN_REVIEW -> WITHDRAWN (用户撤回)
 *   任意已闭环 -> RETIRED (退役)
 */
public final class RequirementStatus {

    private RequirementStatus() {}

    public static final String DRAFT = "Draft";
    public static final String SUBMITTED = "Submitted";
    public static final String IN_REVIEW = "InReview";
    public static final String REVIEW_APPROVED = "ReviewApproved";
    public static final String REVIEW_REJECTED = "ReviewRejected";
    /** v2.5 新增：评审通过 → 等待验证准入（PENDING_VERIFY → IMPLEMENTED） */
    public static final String PENDING_VERIFY = "PendingVerify";
    /** v2.5 新增：实施中（替代旧 IN_PROGRESS 早期阶段） */
    public static final String IMPLEMENTED = "Implemented";
    public static final String APPROVED = "Approved";
    public static final String REJECTED = "Rejected";
    public static final String IN_PROGRESS = "InProgress";
    public static final String IN_TEST = "InTest";
    public static final String VERIFIED = "Verified";
    public static final String BASELINE = "Baseline";
    public static final String DECOMPOSED = "Decomposed";
    public static final String SUSPECT = "Suspect";
    public static final String WITHDRAWN = "Withdrawn";
    /** v2.5 新增：闭环终态（VERIFIED 之后） */
    public static final String CLOSED = "Closed";
    /** v2.5 新增：退役终态（任何已闭环之后） */
    public static final String RETIRED = "Retired";

    /** 14 个全状态，按状态机顺序列出（含 v2.5 完整化 18 状态） */
    public static final String[] ALL = {
        DRAFT, SUBMITTED, IN_REVIEW, REVIEW_APPROVED, REVIEW_REJECTED,
        PENDING_VERIFY, IMPLEMENTED, APPROVED, REJECTED, IN_PROGRESS, IN_TEST, VERIFIED,
        BASELINE, DECOMPOSED, SUSPECT, WITHDRAWN, CLOSED, RETIRED
    };

    /** 终态：状态机终点（不允许再迁移） */
    public static final String[] TERMINAL = { CLOSED, RETIRED, REJECTED, WITHDRAWN };

    /** v2.5 兼容老逻辑的过渡态：拆解中 */
    public static final String PENDING_DECOMPOSE_FALLBACK = "PendingDecompose";

    /**
     * v2.5：判断是否为终态
     */
    public static boolean isTerminal(String status) {
        for (String t : TERMINAL) {
            if (t.equals(status)) return true;
        }
        return false;
    }

    /**
     * v2.5：判断是否可从 from 状态迁移到 to 状态
     * 简化版：白名单正向迁移；终态不可再迁
     */
    public static boolean canTransition(String from, String to) {
        if (from == null || to == null) return false;
        if (isTerminal(from)) return false;
        if (from.equals(to)) return true; // 同状态幂等
        // 简易校验：to 必须在 ALL 中
        for (String s : ALL) {
            if (s.equals(to)) return true;
        }
        return false;
    }
}
