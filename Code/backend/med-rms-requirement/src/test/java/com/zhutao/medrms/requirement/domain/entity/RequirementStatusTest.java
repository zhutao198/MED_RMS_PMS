package com.zhutao.medrms.requirement.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 需求状态机白名单校验测试（看板拖拽约束）
 */
class RequirementStatusTest {

    /** 用户反馈场景：草稿不应能直接拖到评审通过 */
    @Test
    void draft_cannot_jump_to_reviewApproved() {
        assertFalse(RequirementStatus.canTransition("Draft", "ReviewApproved"),
                "草稿直接拖到评审通过必须被拒绝");
    }

    /** 草稿不应能直接拖到待验证准入（跳过整条链路） */
    @Test
    void draft_cannot_jump_to_pendingVerify() {
        assertFalse(RequirementStatus.canTransition("Draft", "PendingVerify"));
    }

    /** 草稿不应能直接拖到已闭环 */
    @Test
    void draft_cannot_jump_to_closed() {
        assertFalse(RequirementStatus.canTransition("Draft", "Closed"));
    }

    /** 合法正向流转：草稿 -> 已提交 */
    @Test
    void draft_to_submitted_allowed() {
        assertTrue(RequirementStatus.canTransition("Draft", "Submitted"));
    }

    /** 合法正向流转：评审通过 -> 已批准 */
    @Test
    void reviewApproved_to_approved_allowed() {
        assertTrue(RequirementStatus.canTransition("ReviewApproved", "Approved"));
    }

    /** 合理回退：评审通过 -> 草稿（撤销评审） */
    @Test
    void reviewApproved_to_draft_allowed() {
        assertTrue(RequirementStatus.canTransition("ReviewApproved", "Draft"));
    }

    /** 评审中 -> 已撤回（PRD 撤回） */
    @Test
    void inReview_to_withdrawn_allowed() {
        assertTrue(RequirementStatus.canTransition("InReview", "Withdrawn"));
    }

    /** 终态不可迁出：已闭环 -> 草稿 */
    @Test
    void terminal_closed_cannot_leave() {
        assertFalse(RequirementStatus.canTransition("Closed", "Draft"));
        assertFalse(RequirementStatus.canTransition("Closed", "InProgress"));
    }

    /** 终态不可迁出：已退役 */
    @Test
    void terminal_retired_cannot_leave() {
        assertFalse(RequirementStatus.canTransition("Retired", "Baseline"));
    }

    /** 已验证不应能跳回草稿（防止误回流） */
    @Test
    void verified_cannot_jump_to_draft() {
        assertFalse(RequirementStatus.canTransition("Verified", "Draft"));
    }

    /** 同状态幂等 */
    @Test
    void same_status_idempotent() {
        assertTrue(RequirementStatus.canTransition("Draft", "Draft"));
    }

    /** Suspect 须走变更流程清除，看板不可手动拖出 */
    @Test
    void suspect_cannot_drag_out() {
        assertFalse(RequirementStatus.canTransition("Suspect", "Draft"));
        assertFalse(RequirementStatus.canTransition("Suspect", "InReview"));
    }
}
