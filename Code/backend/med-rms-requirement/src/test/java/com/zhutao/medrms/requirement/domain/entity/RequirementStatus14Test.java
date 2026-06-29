package com.zhutao.medrms.requirement.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RequirementStatus 14 状态机单元测试（W7-D4）
 */
class RequirementStatus14Test {

    @Test
    @DisplayName("ALL 数组含 18 状态（v2.5 完整化）")
    void all_contains_14_states() {
        assertEquals(18, RequirementStatus.ALL.length);
    }

    @Test
    @DisplayName("v2.5 新增 3 状态：PENDING_VERIFY / IMPLEMENTED / CLOSED / RETIRED")
    void new_states_present() {
        assertEquals("PendingVerify", RequirementStatus.PENDING_VERIFY);
        assertEquals("Implemented", RequirementStatus.IMPLEMENTED);
        assertEquals("Closed", RequirementStatus.CLOSED);
        assertEquals("Retired", RequirementStatus.RETIRED);
    }

    @Test
    @DisplayName("isTerminal-CLOSED/RETIRED/REJECTED/WITHDRAWN 是终态")
    void isTerminal() {
        assertTrue(RequirementStatus.isTerminal("Closed"));
        assertTrue(RequirementStatus.isTerminal("Retired"));
        assertTrue(RequirementStatus.isTerminal("Rejected"));
        assertTrue(RequirementStatus.isTerminal("Withdrawn"));

        assertFalse(RequirementStatus.isTerminal("Draft"));
        assertFalse(RequirementStatus.isTerminal("Approved"));
        assertFalse(RequirementStatus.isTerminal("InProgress"));
    }

    @Test
    @DisplayName("canTransition-同状态幂等允许")
    void canTransition_sameState() {
        assertTrue(RequirementStatus.canTransition("Draft", "Draft"));
        assertTrue(RequirementStatus.canTransition("Approved", "Approved"));
    }

    @Test
    @DisplayName("canTransition-终态不可再迁")
    void canTransition_terminal() {
        assertFalse(RequirementStatus.canTransition("Closed", "Draft"));
        assertFalse(RequirementStatus.canTransition("Retired", "Approved"));
        assertFalse(RequirementStatus.canTransition("Rejected", "InProgress"));
    }

    @Test
    @DisplayName("canTransition-合法 ALL 中目标返回 true")
    void canTransition_valid() {
        assertTrue(RequirementStatus.canTransition("Draft", "Submitted"));
        assertTrue(RequirementStatus.canTransition("Approved", "InProgress"));
        assertTrue(RequirementStatus.canTransition("Verified", "Baseline"));
        // v2.5 新增流转
        assertTrue(RequirementStatus.canTransition("ReviewApproved", "PendingVerify"));
        assertTrue(RequirementStatus.canTransition("PendingVerify", "Implemented"));
        assertTrue(RequirementStatus.canTransition("Verified", "Closed"));
        assertTrue(RequirementStatus.canTransition("Baseline", "Retired"));
    }

    @Test
    @DisplayName("canTransition-null 或非 ALL 状态返回 false")
    void canTransition_invalid() {
        assertFalse(RequirementStatus.canTransition(null, "Draft"));
        assertFalse(RequirementStatus.canTransition("Draft", null));
        assertFalse(RequirementStatus.canTransition("Draft", "InvalidState"));
    }

    @Test
    @DisplayName("兼容老逻辑 - PENDING_DECOMPOSE_FALLBACK 保留")
    void legacy_compat() {
        assertEquals("PendingDecompose", RequirementStatus.PENDING_DECOMPOSE_FALLBACK);
    }
}
