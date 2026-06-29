package com.zhutao.medrms.change.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.change.domain.entity.*;
import com.zhutao.medrms.change.mapper.*;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.outbox.OutboxService;
import com.zhutao.medrms.notification.service.NotificationService;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.domain.entity.RequirementAncestor;
import com.zhutao.medrms.requirement.mapper.RequirementAncestorMapper;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.zhutao.medrms.requirement.mapper.TestCaseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ChangeService 单元测试（W2-D4）
 * 覆盖：创建（基线化校验/MAJOR 会签/OA 推流）/ 提交/审批（FR-0.17 影响评估前置/MAJOR 签名/会签前置）
 *      / 拒绝 / 执行（MAJOR 双签/Part 11）/ EMERGENCY 直执行 / 影响评估 / 验证 / 关闭 / 取消
 *      / 委派 / 会签 / 列表 / 时间线
 */
@ExtendWith(MockitoExtension.class)
class ChangeServiceTest {

    @Mock private ChangeRequestMapper changeRequestMapper;
    @Mock private ImpactAssessmentMapper impactAssessmentMapper;
    @Mock private RequirementMapper requirementMapper;
    @Mock private RequirementAncestorMapper ancestorMapper;
    @Mock private TestCaseMapper testCaseMapper;
    @Mock private NotificationService notificationService;
    @Mock private ChangeApprovalMapper changeApprovalMapper;
    @Mock private ChangeExecutionMapper changeExecutionMapper;
    @Mock private OutboxService outboxService;
    @Mock private ChangeTimelineMapper changeTimelineMapper;
    @Mock private OaIntegrationService oaIntegrationService;

    @InjectMocks private ChangeService service;

    // ============================================================
    // 1. createChangeRequest
    // ============================================================

    @Test
    @DisplayName("createChangeRequest-需求不存在抛 CH0101")
    void createChangeRequest_requirementNotFound() {
        when(requirementMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.createChangeRequest(99L, "MINOR", "reason", "NORMAL", 1L, "title"));
        assertEquals("CH0101", ex.getCode());
    }

    @Test
    @DisplayName("createChangeRequest-未基线化抛 stateConflict")
    void createChangeRequest_notBaseline() {
        Requirement r = new Requirement();
        r.setId(1L);
        r.setStatus("Draft");
        when(requirementMapper.selectById(1L)).thenReturn(r);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.createChangeRequest(1L, "MINOR", "x", "NORMAL", 1L, "t"));
        assertTrue(ex.getMessage().contains("未基线化"));
    }

    @Test
    @DisplayName("createChangeRequest-MINOR 不启用会签")
    void createChangeRequest_minorNoCountersign() {
        Requirement r = new Requirement();
        r.setId(1L);
        r.setStatus("Baseline");
        r.setProjectId(1L);
        when(requirementMapper.selectById(1L)).thenReturn(r);
        when(changeRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        ChangeRequest cr = service.createChangeRequest(1L, "MINOR", "x", "NORMAL", 100L, "t");

        assertNotNull(cr.getChangeNo());
        assertFalse(cr.getCountersignRequired());
        assertEquals("NONE", cr.getCountersignProgress());
        verify(changeRequestMapper).insert(any(ChangeRequest.class));
        verify(changeTimelineMapper).insert(any(ChangeTimelineEntry.class));
    }

    @Test
    @DisplayName("createChangeRequest-MAJOR 启用会签 + 推 OA")
    void createChangeRequest_majorCountersign() {
        Requirement r = new Requirement();
        r.setId(1L);
        r.setStatus("Baseline");
        r.setProjectId(1L);
        when(requirementMapper.selectById(1L)).thenReturn(r);
        when(changeRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(oaIntegrationService.createApprovalWorkflow(any())).thenReturn("OA-WF-001");

        ChangeRequest cr = service.createChangeRequest(1L, "MAJOR", "x", "NORMAL", 100L, "t");

        assertTrue(cr.getCountersignRequired());
        assertEquals("PENDING", cr.getCountersignProgress());
        verify(oaIntegrationService).createApprovalWorkflow(any(ChangeRequest.class));
    }

    @Test
    @DisplayName("createChangeRequest-OA 推流失败不影响主流程")
    void createChangeRequest_oaFailureTolerated() {
        Requirement r = new Requirement();
        r.setId(1L);
        r.setStatus("Baseline");
        r.setProjectId(1L);
        when(requirementMapper.selectById(1L)).thenReturn(r);
        when(changeRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(oaIntegrationService.createApprovalWorkflow(any())).thenThrow(new RuntimeException("OA down"));

        // 不应抛
        ChangeRequest cr = service.createChangeRequest(1L, "MAJOR", "x", "NORMAL", 100L, "t");
        assertNotNull(cr);
    }

    // ============================================================
    // 2. submitChange
    // ============================================================

    @Test
    @DisplayName("submitChange-DRAFT → ANALYZING")
    void submitChange_ok() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setStatus("DRAFT");
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);

        ChangeRequest result = service.submitChange(1L);

        assertEquals("ANALYZING", result.getStatus());
        verify(changeTimelineMapper).insert(any(ChangeTimelineEntry.class));
    }

    @Test
    @DisplayName("submitChange-非 DRAFT 抛 stateConflict")
    void submitChange_invalidState() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setStatus("PENDING_APPROVAL");
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);

        assertThrows(BusinessException.class, () -> service.submitChange(1L));
    }

    // ============================================================
    // 3. approveChange
    // ============================================================

    @Test
    @DisplayName("approveChange-FR-0.17 无影响评估禁止审批")
    void approveChange_noImpact() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setStatus("ANALYZING");
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);
        when(impactAssessmentMapper.countByChangeId(1L)).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.approveChange(1L, 200L, "APPROVED", "ok", null));
        assertTrue(ex.getMessage().contains("FR-0.17"));
    }

    @Test
    @DisplayName("approveChange-MAJOR 必须传 signatureId")
    void approveChange_majorNeedSignature() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setStatus("ANALYZING");
        cr.setChangeType("MAJOR");
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);
        when(impactAssessmentMapper.countByChangeId(1L)).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.approveChange(1L, 200L, "APPROVED", "ok", null));
        assertTrue(ex.getMessage().contains("电子签名"));
    }

    @Test
    @DisplayName("approveChange-启用会签但未完成禁止审批")
    void approveChange_countersignNotCompleted() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setStatus("ANALYZING");
        cr.setChangeType("MINOR");
        cr.setCountersignRequired(true);
        cr.setCountersignProgress("PENDING");
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);
        when(impactAssessmentMapper.countByChangeId(1L)).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.approveChange(1L, 200L, "APPROVED", "ok", null));
        assertTrue(ex.getMessage().contains("会签"));
    }

    @Test
    @DisplayName("approveChange-APPROVED 成功 + 写审批记录 + 事件 + 通知")
    void approveChange_approved() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setChangeNo("CR-1-0001");
        cr.setStatus("PENDING_APPROVAL");
        cr.setChangeType("MINOR");
        cr.setRequestedBy(50L);
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);
        when(impactAssessmentMapper.countByChangeId(1L)).thenReturn(1L);

        service.approveChange(1L, 200L, "APPROVED", "go", 999L);

        assertEquals("APPROVED", cr.getStatus());
        verify(changeApprovalMapper).insert(any(ChangeApproval.class));
        verify(outboxService).append(eq("ChangeApproved"), eq("change"), eq(1L), any());
        verify(notificationService).sendChangeApprovedNotification(eq(50L), any());
    }

    @Test
    @DisplayName("approveChange-REJECTED 状态变 CLOSED + 写拒绝意见")
    void approveChange_rejected() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setChangeNo("CR-1-0001");
        cr.setStatus("PENDING_APPROVAL");
        cr.setChangeType("MINOR");
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);
        when(impactAssessmentMapper.countByChangeId(1L)).thenReturn(1L);

        service.approveChange(1L, 200L, "REJECTED", "no", null);

        assertEquals("CLOSED", cr.getStatus());
        assertTrue(cr.getApprovalComments().contains("拒绝原因"));
        verify(outboxService).append(eq("ChangeRejected"), eq("change"), eq(1L), any());
    }

    // ============================================================
    // 4. rejectChange
    // ============================================================

    @Test
    @DisplayName("rejectChange-ANALYZING → CLOSED")
    void rejectChange_ok() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setStatus("ANALYZING");
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);

        service.rejectChange(1L, "x");

        assertEquals("CLOSED", cr.getStatus());
    }

    @Test
    @DisplayName("rejectChange-DRAFT 不允许拒绝")
    void rejectChange_draft() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setStatus("DRAFT");
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);

        assertThrows(BusinessException.class, () -> service.rejectChange(1L, "x"));
    }

    // ============================================================
    // 5. executeChange
    // ============================================================

    @Test
    @DisplayName("executeChange-MAJOR 需 ≥2 个不同 approver 双签")
    void executeChange_majorDualSign() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setStatus("APPROVED");
        cr.setChangeType("MAJOR");
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);

        // 只有 1 个 approver
        ChangeApproval a = new ChangeApproval();
        a.setApproverId(100L);
        when(changeApprovalMapper.selectByChangeId(1L)).thenReturn(List.of(a));

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.executeChange(1L, null));
        assertTrue(ex.getMessage().contains("双签"));
    }

    @Test
    @DisplayName("executeChange-MAJOR 双签通过 → EXECUTING + 快照 + 事件")
    void executeChange_majorSuccess() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setChangeNo("CR-1-0001");
        cr.setStatus("APPROVED");
        cr.setChangeType("MAJOR");
        cr.setRequirementId(10L);
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);
        ChangeApproval a1 = new ChangeApproval(); a1.setApproverId(100L);
        ChangeApproval a2 = new ChangeApproval(); a2.setApproverId(200L);
        when(changeApprovalMapper.selectByChangeId(1L)).thenReturn(List.of(a1, a2));

        service.executeChange(1L, null);

        assertEquals("EXECUTING", cr.getStatus());
        verify(changeExecutionMapper).insert(any(ChangeExecution.class));
        verify(outboxService).append(eq("ChangeExecuted"), eq("change"), eq(1L), any());
    }

    @Test
    @DisplayName("executeChange-非 APPROVED 抛 stateConflict")
    void executeChange_invalidState() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setStatus("PENDING_APPROVAL");
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);

        assertThrows(BusinessException.class, () -> service.executeChange(1L, null));
    }

    @Test
    @DisplayName("executeChange-传入 updatedRequirement 写入主表")
    void executeChange_withUpdatedReq() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setStatus("APPROVED");
        cr.setChangeType("MINOR");
        cr.setRequirementId(10L);
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);

        Requirement upd = new Requirement();
        upd.setTitle("new title");
        service.executeChange(1L, upd);

        assertEquals(10L, upd.getId());
        verify(requirementMapper).updateById(upd);
    }

    // ============================================================
    // 6. emergencyDirectExecute
    // ============================================================

    @Test
    @DisplayName("emergencyDirectExecute-非 EMERGENCY 拒绝")
    void emergencyDirectExecute_wrongUrgency() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setUrgency("HIGH");
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);

        assertThrows(BusinessException.class, () -> service.emergencyDirectExecute(1L, 100L, "x"));
    }

    @Test
    @DisplayName("emergencyDirectExecute-DRAFT EMERGENCY → EXECUTING + 标记 suspect + 事件")
    void emergencyDirectExecute_ok() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setChangeNo("CR-1-0001");
        cr.setStatus("DRAFT");
        cr.setUrgency("EMERGENCY");
        cr.setRequirementId(10L);
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);
        when(ancestorMapper.selectDescendantIds(10L)).thenReturn(List.of(20L, 30L));

        service.emergencyDirectExecute(1L, 100L, "patient emergency");

        assertEquals("EXECUTING", cr.getStatus());
        verify(requirementMapper).markSuspectBatch(anyString());
        verify(testCaseMapper).markSuspectByRequirementIds(any());
        verify(outboxService).append(eq("ChangeEmergencyExecuted"), eq("change"), eq(1L), any());
    }

    // ============================================================
    // 7. performImpactAssessment
    // ============================================================

    @Test
    @DisplayName("performImpactAssessment-ANALYZING → PENDING_APPROVAL + 写评估 + 标记 suspect")
    void performImpactAssessment_ok() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setStatus("ANALYZING");
        cr.setRequirementId(10L);
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);
        Requirement target = new Requirement();
        target.setId(10L);
        target.setRequirementNo("URS-001-001");
        when(requirementMapper.selectById(10L)).thenReturn(target);

        RequirementAncestor anc1 = new RequirementAncestor();
        anc1.setDepth(1);
        when(ancestorMapper.selectByDescendant(10L)).thenReturn(List.of(anc1));
        when(ancestorMapper.selectDescendants(10L)).thenReturn(Collections.emptyList());

        service.performImpactAssessment(1L);

        verify(impactAssessmentMapper).insert(any(ImpactAssessment.class));
        assertEquals("PENDING_APPROVAL", cr.getStatus());
    }

    @Test
    @DisplayName("performImpactAssessment-影响 >10 → CRITICAL")
    void performImpactAssessment_critical() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setStatus("ANALYZING");
        cr.setRequirementId(10L);
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);
        Requirement target = new Requirement();
        target.setId(10L);
        target.setRequirementNo("URS-001-001");
        when(requirementMapper.selectById(10L)).thenReturn(target);

        List<RequirementAncestor> ancs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            RequirementAncestor a = new RequirementAncestor();
            a.setDepth(1);
            ancs.add(a);
        }
        List<RequirementAncestor> descs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            RequirementAncestor a = new RequirementAncestor();
            a.setDepth(1);
            descs.add(a);
        }
        when(ancestorMapper.selectByDescendant(10L)).thenReturn(ancs);
        when(ancestorMapper.selectDescendants(10L)).thenReturn(descs);

        service.performImpactAssessment(1L);

        ArgumentCaptor<ImpactAssessment> cap = ArgumentCaptor.forClass(ImpactAssessment.class);
        verify(impactAssessmentMapper).insert(cap.capture());
        assertEquals("CRITICAL", cap.getValue().getImpactLevel());
    }

    // ============================================================
    // 8. verifyChange / closeChange / cancelChange
    // ============================================================

    @Test
    @DisplayName("verifyChange-EXECUTING → COMPLETED")
    void verifyChange_ok() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setStatus("EXECUTING");
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);

        service.verifyChange(1L);

        assertEquals("COMPLETED", cr.getStatus());
    }

    @Test
    @DisplayName("closeChange-任意状态可关闭")
    void closeChange_ok() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setStatus("COMPLETED");
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);

        service.closeChange(1L);

        assertEquals("CLOSED", cr.getStatus());
    }

    @Test
    @DisplayName("cancelChange-仅 DRAFT/ANALYZING 可取消")
    void cancelChange_ok() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setStatus("DRAFT");
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);

        service.cancelChange(1L, "放弃");

        assertEquals("CANCELLED", cr.getStatus());
    }

    @Test
    @DisplayName("cancelChange-APPROVED 不允许取消")
    void cancelChange_invalidState() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setStatus("APPROVED");
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);

        assertThrows(BusinessException.class, () -> service.cancelChange(1L, "x"));
    }

    // ============================================================
    // 9. 委派 / 会签
    // ============================================================

    @Test
    @DisplayName("delegate-ANALYZING 可委派")
    void delegate_ok() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setStatus("ANALYZING");
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);

        service.delegate(1L, 100L, "from", 200L, "to");

        assertEquals(200L, cr.getAssigneeId());
        assertNotNull(cr.getDelegatedAt());
    }

    @Test
    @DisplayName("delegate-DRAFT 不允许委派")
    void delegate_invalidState() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setStatus("DRAFT");
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);

        assertThrows(BusinessException.class, () -> service.delegate(1L, 100L, "from", 200L, "to"));
    }

    @Test
    @DisplayName("setCountersigners-空列表抛 param")
    void setCountersigners_empty() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);

        assertThrows(BusinessException.class, () -> service.setCountersigners(1L, List.of()));
        assertThrows(BusinessException.class, () -> service.setCountersigners(1L, null));
    }

    @Test
    @DisplayName("setCountersigners-正常添加")
    void setCountersigners_ok() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);

        Map<String, Object> s1 = new LinkedHashMap<>();
        s1.put("id", 100L);
        s1.put("name", "alice");
        service.setCountersigners(1L, List.of(s1));

        assertTrue(cr.getCountersignRequired());
        assertEquals("PENDING", cr.getCountersignProgress());
        assertNotNull(cr.getCountersigners());
    }

    @Test
    @DisplayName("countersign-未启用会签抛 stateConflict")
    void countersign_notRequired() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setCountersignRequired(false);
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);

        assertThrows(BusinessException.class, () -> service.countersign(1L, 100L, "ok"));
    }

    @Test
    @DisplayName("countersign-单签 + 全部签完 → COMPLETED")
    void countersign_completed() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setCountersignRequired(true);
        cr.setCountersignProgress("PENDING");
        // 单个会签人
        String raw = "[{\"id\":100,\"name\":\"alice\",\"signed\":false}]";
        cr.setCountersigners(raw);
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);

        service.countersign(1L, 100L, "ok");

        assertEquals("COMPLETED", cr.getCountersignProgress());
    }

    @Test
    @DisplayName("countersign-不在会签人列表")
    void countersign_notInList() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setCountersignRequired(true);
        cr.setCountersignProgress("PENDING");
        cr.setCountersigners("[{\"id\":100,\"name\":\"alice\",\"signed\":false}]");
        when(changeRequestMapper.selectById(1L)).thenReturn(cr);

        assertThrows(BusinessException.class, () -> service.countersign(1L, 999L, "ok"));
    }

    // ============================================================
    // 10. 查询 / 列表
    // ============================================================

    @Test
    @DisplayName("getChangeById-不存在抛 CH0101")
    void getChangeById_notFound() {
        when(changeRequestMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getChangeById(99L));
    }

    @Test
    @DisplayName("getChangesByRequirement-透传")
    void getChangesByRequirement() {
        when(changeRequestMapper.selectByRequirementId(1L)).thenReturn(List.of(new ChangeRequest()));
        assertEquals(1, service.getChangesByRequirement(1L).size());
    }

    @Test
    @DisplayName("getPendingApprovals-按状态查")
    void getPendingApprovals() {
        when(changeRequestMapper.selectByStatus("PENDING_APPROVAL")).thenReturn(List.of(new ChangeRequest()));
        assertEquals(1, service.getPendingApprovals().size());
    }

    @Test
    @DisplayName("listByConditions-空过滤")
    void listByConditions_empty() {
        when(changeRequestMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        assertTrue(service.listByConditions(null, null, 1, 20).isEmpty());
    }

    @Test
    @DisplayName("getTimeline-透传")
    void getTimeline() {
        when(changeTimelineMapper.selectByChangeId(1L)).thenReturn(List.of(new ChangeTimelineEntry()));
        assertEquals(1, service.getTimeline(1L).size());
    }
}
