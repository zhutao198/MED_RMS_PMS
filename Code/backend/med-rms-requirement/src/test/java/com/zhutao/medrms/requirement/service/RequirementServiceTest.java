package com.zhutao.medrms.requirement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.util.PageRequest;
import com.zhutao.medrms.notification.service.NotificationService;
import com.zhutao.medrms.requirement.domain.entity.*;
import com.zhutao.medrms.requirement.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RequirementService 单元测试（W2-D1）
 * 覆盖：列表/分页/详情/创建/批量创建/拆解/更新/评审（单+多 reviewer）/投票/审批/状态机
 *      /看板分组/需求树/撤回/撤销/Suspect/开始实施/测试/验证/编号查询
 */
@ExtendWith(MockitoExtension.class)
class RequirementServiceTest {

    @Mock private RequirementMapper requirementMapper;
    @Mock private RequirementAncestorMapper ancestorMapper;
    @Mock private UserRequirementMapper userRequirementMapper;
    @Mock private ProductRequirementMapper productRequirementMapper;
    @Mock private SystemRequirementMapper systemRequirementMapper;
    @Mock private DesignRequirementMapper designRequirementMapper;
    @Mock private ReviewMapper reviewMapper;
    @Mock private NotificationService notificationService;

    @InjectMocks private RequirementService service;

    // ============================================================
    // 1. 列表与分页
    // ============================================================

    @Test
    @DisplayName("listRequirements-无过滤返回全部分页结果")
    void listRequirements_noFilter_returnsAll() {
        PageRequest pr = new PageRequest();
        pr.setPage(1); pr.setSize(20);
        Page<Requirement> page = new Page<>(1, 20);
        page.setRecords(List.of(req(1L, "URS")));
        page.setTotal(1);
        when(requirementMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        IPage<Requirement> result = service.listRequirements(1L, null, null, null, null, pr);

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("listRequirements-带 type+status+keyword 过滤")
    void listRequirements_withFilters() {
        Page<Requirement> page = new Page<>(1, 20);
        when(requirementMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        IPage<Requirement> result = service.listRequirements(1L, "URS", "Draft", "P1", "alarm", 1, 20);

        assertNotNull(result);
        verify(requirementMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("listRequirements-page 归一化（负数 → 1）")
    void listRequirements_pageNormalized() {
        Page<Requirement> page = new Page<>(1, 20);
        when(requirementMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageRequest pr = new PageRequest();
        pr.setPage(-1); pr.setSize(0); // 触发 normalize
        service.listRequirements(1L, null, null, pr);

        ArgumentCaptor<Page> pageCap = ArgumentCaptor.forClass(Page.class);
        verify(requirementMapper).selectPage(pageCap.capture(), any(LambdaQueryWrapper.class));
        assertTrue(pageCap.getValue().getCurrent() >= 1);
        assertTrue(pageCap.getValue().getSize() > 0);
    }

    // ============================================================
    // 2. 看板分组
    // ============================================================

    @Test
    @DisplayName("listGroupedByStatus-返回 14 个状态分组")
    void listGroupedByStatus_returns14Groups() {
        List<Requirement> all = List.of(
            req(1L, "URS", "Draft"),
            req(2L, "URS", "Submitted"),
            req(3L, "URS", "InReview")
        );
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(all);

        Map<String, List<Requirement>> result = service.listGroupedByStatus(1L);

        assertEquals(14, result.size(), "应包含 14 个状态组");
        assertEquals(1, result.get("Draft").size());
        assertEquals(1, result.get("Submitted").size());
        assertEquals(1, result.get("InReview").size());
    }

    @Test
    @DisplayName("listGroupedByStatus-null 状态归入 Draft")
    void listGroupedByStatus_nullStatusGoesToDraft() {
        Requirement r = req(1L, "URS", null);
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(r));

        Map<String, List<Requirement>> result = service.listGroupedByStatus(1L);

        assertEquals(1, result.get("Draft").size());
    }

    // ============================================================
    // 3. 详情
    // ============================================================

    @Test
    @DisplayName("getRequirementById-存在则返回")
    void getRequirementById_exists() {
        Requirement r = req(1L, "URS");
        when(requirementMapper.selectById(1L)).thenReturn(r);

        assertSame(r, service.getRequirementById(1L));
    }

    @Test
    @DisplayName("getRequirementById-不存在抛 RQ0101")
    void getRequirementById_notFound() {
        when(requirementMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getRequirementById(99L));
        assertEquals("RQ0101", ex.getCode());
    }

    // ============================================================
    // 4. 创建（含编号生成 + 闭包表 + 子表）
    // ============================================================

    @Test
    @DisplayName("createRequirement-URS 写主表+URS子表+自引用闭包")
    void createRequirement_URS() {
        try (MockedStatic<com.zhutao.medrms.common.util.SecurityUtils> mocked =
                 Mockito.mockStatic(com.zhutao.medrms.common.util.SecurityUtils.class)) {
            mocked.when(com.zhutao.medrms.common.util.SecurityUtils::getCurrentUserId).thenReturn(100L);

            Requirement input = new Requirement();
            input.setProjectId(1L);
            input.setRequirementType("URS");
            input.setTitle("test");
            when(requirementMapper.countByProject(1L)).thenReturn(0L);

            service.createRequirement(input, null);

            verify(requirementMapper).insert(any(Requirement.class));
            verify(userRequirementMapper).insert(any(UserRequirement.class));
            verify(productRequirementMapper, never()).insert(any(ProductRequirement.class));
            verify(ancestorMapper).insert(any(RequirementAncestor.class));
        }
    }

    @Test
    @DisplayName("createRequirement-PRS 写 PRS 子表（不写 URS）")
    void createRequirement_PRS() {
        try (MockedStatic<com.zhutao.medrms.common.util.SecurityUtils> mocked =
                 Mockito.mockStatic(com.zhutao.medrms.common.util.SecurityUtils.class)) {
            mocked.when(com.zhutao.medrms.common.util.SecurityUtils::getCurrentUserId).thenReturn(100L);

            Requirement input = new Requirement();
            input.setProjectId(1L);
            input.setRequirementType("PRS");
            when(requirementMapper.countByProject(1L)).thenReturn(0L);

            service.createRequirement(input, null);

            verify(productRequirementMapper).insert(any(ProductRequirement.class));
            verify(userRequirementMapper, never()).insert(any(UserRequirement.class));
        }
    }

    @Test
    @DisplayName("createRequirement-DRS 写 DRS 子表")
    void createRequirement_DRS() {
        try (MockedStatic<com.zhutao.medrms.common.util.SecurityUtils> mocked =
                 Mockito.mockStatic(com.zhutao.medrms.common.util.SecurityUtils.class)) {
            mocked.when(com.zhutao.medrms.common.util.SecurityUtils::getCurrentUserId).thenReturn(100L);

            Requirement input = new Requirement();
            input.setProjectId(1L);
            input.setRequirementType("DRS");
            when(requirementMapper.countByProject(1L)).thenReturn(0L);

            service.createRequirement(input, null);

            verify(designRequirementMapper).insert(any(DesignRequirement.class));
        }
    }

    // ============================================================
    // 5. 批量创建（容错）
    // ============================================================

    @Test
    @DisplayName("createBatchRequirements-空列表抛 param 异常")
    void createBatchRequirements_empty() {
        assertThrows(BusinessException.class, () -> service.createBatchRequirements(Collections.emptyList()));
        assertThrows(BusinessException.class, () -> service.createBatchRequirements(null));
    }

    @Test
    @DisplayName("createBatchRequirements-单条失败不阻断其他")
    void createBatchRequirements_partialFailure() {
        try (MockedStatic<com.zhutao.medrms.common.util.SecurityUtils> mocked =
                 Mockito.mockStatic(com.zhutao.medrms.common.util.SecurityUtils.class)) {
            mocked.when(com.zhutao.medrms.common.util.SecurityUtils::getCurrentUserId).thenReturn(null);

            Requirement r1 = new Requirement();
            r1.setProjectId(1L);
            r1.setRequirementType("URS");
            r1.setTitle("r1");
            Requirement r2 = new Requirement();
            r2.setProjectId(1L);
            r2.setRequirementType("XXX"); // 未知类型，子表无操作

            when(requirementMapper.countByProject(1L)).thenReturn(0L);

            List<Requirement> result = service.createBatchRequirements(List.of(r1, r2));

            // r1 成功，r2 成功（无子表写）
            assertEquals(2, result.size());
        }
    }

    // ============================================================
    // 6. 拆解
    // ============================================================

    @Test
    @DisplayName("decomposeRequirement-URS→PRS 成功 + 父级变 DECOMPOSED")
    void decomposeRequirement_URS_to_PRS() {
        try (MockedStatic<com.zhutao.medrms.common.util.SecurityUtils> mocked =
                 Mockito.mockStatic(com.zhutao.medrms.common.util.SecurityUtils.class)) {
            mocked.when(com.zhutao.medrms.common.util.SecurityUtils::getCurrentUserId).thenReturn(100L);

            Requirement parent = req(1L, "URS", "Draft");
            when(requirementMapper.selectById(1L)).thenReturn(parent);
            when(requirementMapper.countByProject(1L)).thenReturn(0L);
            when(ancestorMapper.selectByDescendant(1L)).thenReturn(Collections.emptyList());
            when(ancestorMapper.selectDescendants(1L)).thenReturn(Collections.emptyList());

            Requirement child = new Requirement();
            child.setRequirementType("PRS");
            child.setTitle("child");

            service.decomposeRequirement(1L, child, null);

            ArgumentCaptor<Requirement> parentCap = ArgumentCaptor.forClass(Requirement.class);
            verify(requirementMapper, atLeastOnce()).updateById(parentCap.capture());
            assertEquals(RequirementStatus.DECOMPOSED, parentCap.getValue().getStatus());
        }
    }

    @Test
    @DisplayName("decomposeRequirement-非法层级 URS→SRS 抛 param")
    void decomposeRequirement_invalidLevel() {
        Requirement parent = req(1L, "URS", "Draft");
        when(requirementMapper.selectById(1L)).thenReturn(parent);

        Requirement child = new Requirement();
        child.setRequirementType("SRS");

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.decomposeRequirement(1L, child, null));
        assertTrue(ex.getMessage().contains("URS只能拆解为"));
    }

    @Test
    @DisplayName("decomposeRequirement-PRS→DRS 抛 param")
    void decomposeRequirement_PRS_to_DRS() {
        Requirement parent = req(1L, "PRS", "Draft");
        when(requirementMapper.selectById(1L)).thenReturn(parent);

        Requirement child = new Requirement();
        child.setRequirementType("DRS");

        assertThrows(BusinessException.class, () -> service.decomposeRequirement(1L, child, null));
    }

    // ============================================================
    // 7. 更新
    // ============================================================

    @Test
    @DisplayName("updateRequirement-部分字段更新")
    void updateRequirement_partial() {
        try (MockedStatic<com.zhutao.medrms.common.util.SecurityUtils> mocked =
                 Mockito.mockStatic(com.zhutao.medrms.common.util.SecurityUtils.class)) {
            mocked.when(com.zhutao.medrms.common.util.SecurityUtils::getCurrentUserId).thenReturn(100L);

            Requirement existing = req(1L, "URS", "Draft");
            existing.setTitle("OLD");
            when(requirementMapper.selectById(1L)).thenReturn(existing);
            when(requirementMapper.selectById(1L)).thenReturn(existing);

            Requirement upd = new Requirement();
            upd.setTitle("NEW");
            service.updateRequirement(1L, upd);

            verify(requirementMapper).updateFields(eq(1L), eq("NEW"), any(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    // ============================================================
    // 8. 评审（单 reviewer）
    // ============================================================

    @Test
    @DisplayName("submitForReview-单 reviewer 旧逻辑直接 APPROVED")
    void submitForReview_single() {
        Requirement r = req(1L, "URS", "Draft");
        when(requirementMapper.selectById(1L)).thenReturn(r);
        when(reviewMapper.countByRequirement(1L)).thenReturn(0L);

        service.submitForReview(1L, 200L, "reviewer1", "ok", null);

        ArgumentCaptor<Review> reviewCap = ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper).insert(reviewCap.capture());
        assertEquals("APPROVED", reviewCap.getValue().getDecision());
        assertEquals("APPROVED", reviewCap.getValue().getFinalDecision());
        assertTrue(reviewCap.getValue().getAutoSubmitted());
        verify(notificationService).sendReviewApprovedNotification(any(), eq(1L), any());
    }

    @Test
    @DisplayName("submitForReview-状态非 Draft/DECOMPOSED 抛 stateConflict")
    void submitForReview_invalidState() {
        Requirement r = req(1L, "URS", "Approved");
        when(requirementMapper.selectById(1L)).thenReturn(r);

        assertThrows(BusinessException.class,
            () -> service.submitForReview(1L, 200L, "ok"));
    }

    // ============================================================
    // 9. 评审（多 reviewer 投票）
    // ============================================================

    @Test
    @DisplayName("submitForReview-多 reviewer 启动 PENDING 状态")
    void submitForReview_multi() {
        Requirement r = req(1L, "URS", "Draft");
        when(requirementMapper.selectById(1L)).thenReturn(r);
        when(reviewMapper.countByRequirement(1L)).thenReturn(0L);

        service.submitForReview(1L, 200L, "r1", "ok", List.of(201L, 202L));

        verify(reviewMapper, times(3)).insert(any(Review.class)); // 3 reviewer
    }

    @Test
    @DisplayName("castReviewVote-非法 decision 抛 param")
    void castReviewVote_invalidDecision() {
        assertThrows(BusinessException.class,
            () -> service.castReviewVote(1L, 200L, "MAYBE", "x"));
    }

    @Test
    @DisplayName("castReviewVote-需求不在评审中抛 stateConflict")
    void castReviewVote_notInReview() {
        Requirement r = req(1L, "URS", "Draft");
        when(requirementMapper.selectById(1L)).thenReturn(r);

        assertThrows(BusinessException.class,
            () -> service.castReviewVote(1L, 200L, "APPROVED", "x"));
    }

    @Test
    @DisplayName("castReviewVote-全 APPROVED 收口为 APPROVED")
    void castReviewVote_allApproved() {
        Requirement r = req(1L, "URS", "InReview");
        when(requirementMapper.selectById(1L)).thenReturn(r);
        Review mine = new Review(); mine.setReviewerId(200L); mine.setDecision("PENDING"); mine.setRound(1);
        Review r2 = new Review(); r2.setReviewerId(201L); r2.setDecision("APPROVED"); r2.setRound(1);
        when(reviewMapper.selectLatestRoundByRequirement(1L)).thenReturn(List.of(mine, r2));

        service.castReviewVote(1L, 200L, "APPROVED", "ok");

        assertEquals("APPROVED", mine.getDecision());
        assertEquals("APPROVED", r2.getFinalDecision());
        verify(reviewMapper, atLeast(2)).updateById(any(Review.class));
    }

    @Test
    @DisplayName("castReviewVote-一票 REJECTED 整体 REJECTED")
    void castReviewVote_anyRejected() {
        Requirement r = req(1L, "URS", "InReview");
        when(requirementMapper.selectById(1L)).thenReturn(r);
        Review mine = new Review(); mine.setReviewerId(200L); mine.setDecision("PENDING"); mine.setRound(1);
        Review r2 = new Review(); r2.setReviewerId(201L); r2.setDecision("REJECTED"); r2.setRound(1);
        when(reviewMapper.selectLatestRoundByRequirement(1L)).thenReturn(List.of(mine, r2));

        service.castReviewVote(1L, 200L, "APPROVED", "x");

        // r2 已经被前面的 reviewer 投 REJECTED，mine 投 APPROVED → finalDecision=REJECTED
        assertEquals("REJECTED", r2.getFinalDecision());
    }

    @Test
    @DisplayName("castReviewVote-找不到自己评审记录")
    void castReviewVote_selfNotFound() {
        Requirement r = req(1L, "URS", "InReview");
        when(requirementMapper.selectById(1L)).thenReturn(r);
        Review other = new Review(); other.setReviewerId(999L); other.setDecision("PENDING");
        when(reviewMapper.selectLatestRoundByRequirement(1L)).thenReturn(List.of(other));

        assertThrows(BusinessException.class,
            () -> service.castReviewVote(1L, 200L, "APPROVED", "x"));
    }

    // ============================================================
    // 10. 审批
    // ============================================================

    @Test
    @DisplayName("approveRequirement-APPROVED + 有评审记录 → 状态 APPROVED")
    void approveRequirement_approved() {
        Requirement r = req(1L, "URS", "ReviewApproved");
        when(requirementMapper.selectById(1L)).thenReturn(r);
        when(reviewMapper.countApprovedByRequirement(1L)).thenReturn(1L);

        service.approveRequirement(1L, "APPROVED", 300L, "ok");

        ArgumentCaptor<Requirement> cap = ArgumentCaptor.forClass(Requirement.class);
        verify(requirementMapper, atLeastOnce()).updateById(cap.capture());
        assertEquals(RequirementStatus.APPROVED, cap.getValue().getStatus());
        verify(notificationService).sendReviewApprovedNotification(any(), eq(1L), any());
    }

    @Test
    @DisplayName("approveRequirement-REJECTED 走通知")
    void approveRequirement_rejected() {
        Requirement r = req(1L, "URS", "ReviewApproved");
        when(requirementMapper.selectById(1L)).thenReturn(r);
        when(reviewMapper.countApprovedByRequirement(1L)).thenReturn(1L);

        service.approveRequirement(1L, "REJECTED", 300L, "no");

        verify(notificationService).sendReviewRejectedNotification(any(), any(), eq(1L), any());
    }

    @Test
    @DisplayName("approveRequirement-状态非 ReviewApproved 抛 stateConflict")
    void approveRequirement_invalidState() {
        Requirement r = req(1L, "URS", "Draft");
        when(requirementMapper.selectById(1L)).thenReturn(r);

        assertThrows(BusinessException.class,
            () -> service.approveRequirement(1L, "APPROVED", 300L, "x"));
    }

    @Test
    @DisplayName("approveRequirement-FR-0.17 无评审记录禁止审批")
    void approveRequirement_noReviewRecord() {
        Requirement r = req(1L, "URS", "ReviewApproved");
        when(requirementMapper.selectById(1L)).thenReturn(r);
        when(reviewMapper.countApprovedByRequirement(1L)).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.approveRequirement(1L, "APPROVED", 300L, "x"));
        assertTrue(ex.getMessage().contains("FR-0.17"));
    }

    // ============================================================
    // 11. 状态切换（看板拖拽）
    // ============================================================

    @Test
    @DisplayName("changeStatus-合法状态推进")
    void changeStatus_valid() {
        Requirement r = req(1L, "URS", "Draft");
        when(requirementMapper.selectById(1L)).thenReturn(r);

        Requirement result = service.changeStatus(1L, "Submitted");

        assertEquals("Submitted", result.getStatus());
    }

    @Test
    @DisplayName("changeStatus-同状态幂等返回")
    void changeStatus_same() {
        Requirement r = req(1L, "URS", "Draft");
        when(requirementMapper.selectById(1L)).thenReturn(r);

        Requirement result = service.changeStatus(1L, "Draft");

        assertEquals("Draft", result.getStatus());
        verify(requirementMapper, never()).updateById(any(Requirement.class));
    }

    @Test
    @DisplayName("changeStatus-非法状态抛 param")
    void changeStatus_invalid() {
        assertThrows(BusinessException.class, () -> service.changeStatus(1L, "InvalidStatus"));
        assertThrows(BusinessException.class, () -> service.changeStatus(1L, ""));
        assertThrows(BusinessException.class, () -> service.changeStatus(1L, null));
    }

    // ============================================================
    // 12. 撤销评审
    // ============================================================

    @Test
    @DisplayName("revertToDraft-创建人本人可撤销")
    void revertToDraft_owner() {
        Requirement r = req(1L, "URS", "ReviewApproved");
        r.setCreatedBy(100L);
        when(requirementMapper.selectById(1L)).thenReturn(r);

        service.revertToDraft(1L, 100L, "rollback");

        ArgumentCaptor<Requirement> cap = ArgumentCaptor.forClass(Requirement.class);
        verify(requirementMapper).updateById(cap.capture());
        assertEquals(RequirementStatus.DRAFT, cap.getValue().getStatus());
    }

    @Test
    @DisplayName("revertToDraft-非创建人抛 forbidden")
    void revertToDraft_notOwner() {
        Requirement r = req(1L, "URS", "ReviewApproved");
        r.setCreatedBy(100L);
        when(requirementMapper.selectById(1L)).thenReturn(r);

        assertThrows(BusinessException.class, () -> service.revertToDraft(1L, 999L, "x"));
    }

    // ============================================================
    // 13. 状态推进：startProgress / startTest / verifyRequirement
    // ============================================================

    @Test
    @DisplayName("startProgress-APPROVED → InProgress")
    void startProgress_ok() {
        Requirement r = req(1L, "URS", "Approved");
        when(requirementMapper.selectById(1L)).thenReturn(r);

        service.startProgress(1L);

        assertEquals(RequirementStatus.IN_PROGRESS, r.getStatus());
    }

    @Test
    @DisplayName("startProgress-非 APPROVED 抛 stateConflict")
    void startProgress_invalidState() {
        Requirement r = req(1L, "URS", "Draft");
        when(requirementMapper.selectById(1L)).thenReturn(r);

        assertThrows(BusinessException.class, () -> service.startProgress(1L));
    }

    @Test
    @DisplayName("startTest-InProgress → InTest")
    void startTest_ok() {
        Requirement r = req(1L, "URS", "InProgress");
        when(requirementMapper.selectById(1L)).thenReturn(r);

        service.startTest(1L);

        assertEquals(RequirementStatus.IN_TEST, r.getStatus());
    }

    @Test
    @DisplayName("startTest-非 InProgress 抛 stateConflict")
    void startTest_invalidState() {
        Requirement r = req(1L, "URS", "Draft");
        when(requirementMapper.selectById(1L)).thenReturn(r);

        assertThrows(BusinessException.class, () -> service.startTest(1L));
    }

    @Test
    @DisplayName("verifyRequirement-InTest → Verified")
    void verifyRequirement_ok() {
        Requirement r = req(1L, "URS", "InTest");
        r.setCreatedBy(100L);
        when(requirementMapper.selectById(1L)).thenReturn(r);

        service.verifyRequirement(1L, 200L, "ok");

        assertEquals(RequirementStatus.VERIFIED, r.getStatus());
    }

    @Test
    @DisplayName("verifyRequirement-非 InTest 抛 stateConflict")
    void verifyRequirement_invalidState() {
        Requirement r = req(1L, "URS", "InProgress");
        when(requirementMapper.selectById(1L)).thenReturn(r);

        assertThrows(BusinessException.class, () -> service.verifyRequirement(1L, 200L, "x"));
    }

    // ============================================================
    // 14. 撤回
    // ============================================================

    @Test
    @DisplayName("withdrawRequirement-创建人本人可撤回")
    void withdraw_owner() {
        Requirement r = req(1L, "URS", "Draft");
        r.setCreatedBy(100L);
        when(requirementMapper.selectById(1L)).thenReturn(r);

        service.withdrawRequirement(1L, 100L, "abandon");

        assertEquals(RequirementStatus.WITHDRAWN, r.getStatus());
    }

    @Test
    @DisplayName("withdrawRequirement-非创建人抛 forbidden")
    void withdraw_notOwner() {
        Requirement r = req(1L, "URS", "Draft");
        r.setCreatedBy(100L);
        when(requirementMapper.selectById(1L)).thenReturn(r);

        assertThrows(BusinessException.class, () -> service.withdrawRequirement(1L, 999L, "x"));
    }

    @Test
    @DisplayName("withdrawRequirement-非允许状态抛 stateConflict")
    void withdraw_invalidState() {
        Requirement r = req(1L, "URS", "Approved");
        r.setCreatedBy(100L);
        when(requirementMapper.selectById(1L)).thenReturn(r);

        assertThrows(BusinessException.class, () -> service.withdrawRequirement(1L, 100L, "x"));
    }

    // ============================================================
    // 15. markSuspect
    // ============================================================

    @Test
    @DisplayName("markSuspect-任意状态可标记")
    void markSuspect() {
        Requirement r = req(1L, "URS", "InProgress");
        when(requirementMapper.selectById(1L)).thenReturn(r);

        service.markSuspect(1L, "trace change");

        assertEquals(RequirementStatus.SUSPECT, r.getStatus());
        assertTrue(r.getIsSuspect());
    }

    // ============================================================
    // 16. 需求树
    // ============================================================

    @Test
    @DisplayName("getRequirementTree-闭包表构造树")
    void getRequirementTree() {
        Requirement root = req(1L, "URS", "Draft");
        Requirement child = req(2L, "PRS", "Draft");
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(root, child));

        RequirementAncestor selfRef = new RequirementAncestor();
        selfRef.setAncestorId(1L); selfRef.setDescendantId(1L); selfRef.setDepth(0);
        RequirementAncestor link = new RequirementAncestor();
        link.setAncestorId(1L); link.setDescendantId(2L); link.setDepth(1);
        // R118 性能优化后 getRequirementTree 走批量 selectList(IN ...)，
        // 不再调 selectByDescendant(id)；mock 旧接口无效，必须 mock 批量接口
        when(ancestorMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(selfRef, link));

        List<Map<String, Object>> tree = service.getRequirementTree(1L);

        assertEquals(1, tree.size());
        assertEquals(1L, ((Requirement) tree.get(0).get("requirement")).getId());
    }

    // ============================================================
    // 17. listByUser / getByRequirementNo
    // ============================================================

    @Test
    @DisplayName("listByUser-按 userId 过滤")
    void listByUser() {
        Page<Requirement> page = new Page<>(1, 20);
        when(requirementMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        IPage<Requirement> result = service.listByUser(100L, "URS", 1, 20);

        assertNotNull(result);
    }

    @Test
    @DisplayName("getByRequirementNo-精确查询")
    void getByRequirementNo() {
        Requirement r = req(1L, "URS");
        when(requirementMapper.selectByRequirementNo("URS-001-001")).thenReturn(r);

        assertSame(r, service.getByRequirementNo("URS-001-001"));
    }

    // ============================================================
    // 18. 编号生成（间接：count 探针 + 重试）
    // ============================================================

    @Test
    @DisplayName("createRequirement-编号格式正确（含项目ID）")
    void generateRequirementNo_format() {
        try (MockedStatic<com.zhutao.medrms.common.util.SecurityUtils> mocked =
                 Mockito.mockStatic(com.zhutao.medrms.common.util.SecurityUtils.class)) {
            mocked.when(com.zhutao.medrms.common.util.SecurityUtils::getCurrentUserId).thenReturn(null);

            Requirement input = new Requirement();
            input.setProjectId(5L);
            input.setRequirementType("URS");
            when(requirementMapper.countByProject(5L)).thenReturn(7L);
            when(requirementMapper.selectByRequirementNo(anyString())).thenReturn(null);

            service.createRequirement(input, null);

            ArgumentCaptor<Requirement> cap = ArgumentCaptor.forClass(Requirement.class);
            verify(requirementMapper).insert(cap.capture());
            assertEquals("URS-005-008", cap.getValue().getRequirementNo());
        }
    }

    // ============================================================
    // helper
    // ============================================================

    private Requirement req(Long id, String type) {
        return req(id, type, "Draft");
    }

    private Requirement req(Long id, String type, String status) {
        Requirement r = new Requirement();
        r.setId(id);
        r.setRequirementType(type);
        r.setStatus(status);
        r.setTitle("req-" + id);
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        return r;
    }
}
