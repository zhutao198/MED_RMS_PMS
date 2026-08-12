package com.zhutao.medrms.requirement.domain.entity;

/**
 * v1.47 BUG #125 P0 修复 + v2.5 完整化：需求 18 状态机
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

    /** 终态：状态机终点（不允许再迁移）。REJECTED 可重新提交、BASELINE 可退役，故非终态 */
    public static final String[] TERMINAL = { CLOSED, RETIRED, WITHDRAWN };

    /** v2.5 兼容老逻辑的过渡态：拆解中 */
    public static final String PENDING_DECOMPOSE_FALLBACK = "PendingDecompose";

    /**
     * v2.5 状态机白名单（受控迁移）。
     * 覆盖状态迁移图全部正向边 + PRD 允许的合理回退边（撤销评审回草稿、已驳回重新提交、撤回）。
     * SUSPECT/WITHDRAWN/RETIRED 等需经专门流程（变更/退役），看板不可手动拖入或拖出。
     */
    private static final java.util.Map<String, java.util.Set<String>> TRANSITIONS = new java.util.LinkedHashMap<>();
    static {
        put(DRAFT,          SUBMITTED, IN_REVIEW, DECOMPOSED);
        put(SUBMITTED,      IN_REVIEW, WITHDRAWN, DECOMPOSED);
        put(IN_REVIEW,      REVIEW_APPROVED, REVIEW_REJECTED, WITHDRAWN, DRAFT);
        put(REVIEW_APPROVED, PENDING_VERIFY, APPROVED, REJECTED, DRAFT);
        put(REVIEW_REJECTED, DRAFT, IN_REVIEW);
        put(PENDING_VERIFY,  IMPLEMENTED);
        put(IMPLEMENTED,     IN_PROGRESS);
        put(APPROVED,        IN_PROGRESS, BASELINE);
        put(REJECTED,        DRAFT, IN_REVIEW);
        put(IN_PROGRESS,     IN_TEST, SUSPECT);
        put(IN_TEST,         VERIFIED, SUSPECT);
        put(VERIFIED,        CLOSED, BASELINE, SUSPECT);
        put(CLOSED,          RETIRED);
        put(BASELINE,        RETIRED);
        put(DECOMPOSED,      IN_REVIEW);
        // SUSPECT / WITHDRAWN / RETIRED：看板手动拖拽不允许（须走变更/退役流程），集合为空
    }

    private static void put(String from, String... tos) {
        java.util.Set<String> set = new java.util.LinkedHashSet<>();
        for (String t : tos) set.add(t);
        TRANSITIONS.put(from, set);
    }

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
     * v2.5：判断是否可从 from 状态迁移到 to 状态（严格状态机白名单）。
     * - 终态（CLOSED/RETIRED/WITHDRAWN）不可再迁出
     * - 仅允许白名单内正向/回退边，杜绝草稿→评审通过/待验证准入等跳跃
     */
    public static boolean canTransition(String from, String to) {
        if (from == null || to == null) return false;
        if (from.equals(to)) return true; // 同状态幂等
        if (isTerminal(from)) return false;
        java.util.Set<String> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
}
