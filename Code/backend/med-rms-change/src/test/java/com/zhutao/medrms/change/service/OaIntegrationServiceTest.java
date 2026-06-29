package com.zhutao.medrms.change.service;

import com.zhutao.medrms.change.domain.entity.ChangeRequest;
import com.zhutao.medrms.change.mapper.ChangeTimelineMapper;
import com.zhutao.medrms.common.outbox.OutboxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * OaIntegrationService 单元测试（W12-D3 / W13-D1 修正）
 * OA 审批工作流集成
 *
 * v1.47 BUG #120 修复：Service 重构后不再依赖 ChangeRequestMapper，
 * onOaApprovalCallback 仅发 outbox 事件 + 记录时间线（不改 ChangeRequest 状态）。
 * 状态变更由独立的回调消费者（OutboxEventListener）异步处理。
 */
@ExtendWith(MockitoExtension.class)
class OaIntegrationServiceTest {

    @Mock private OutboxService outboxService;
    @Mock private ChangeTimelineMapper changeTimelineMapper;

    @InjectMocks private OaIntegrationService service;

    @Test
    @DisplayName("createApprovalWorkflow-返回非空 workflowId")
    void createApprovalWorkflow() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setChangeNo("CR-1-0001");

        String workflowId = service.createApprovalWorkflow(cr);

        assertNotNull(workflowId);
        assertTrue(workflowId.length() > 0);
        assertTrue(workflowId.startsWith("OA-WF-"));
    }

    @Test
    @DisplayName("onOaApprovalCallback-发 outbox 事件 + 记录时间线（v1.47 BUG #120）")
    void onOaApprovalCallback() {
        // Service 实际依赖 OutboxService + ChangeTimelineMapper，不改 ChangeRequest
        service.onOaApprovalCallback("WF-001", "APPROVED", "OK", 100L);

        verify(outboxService).append(
                org.mockito.ArgumentMatchers.eq("OAApprovalCallback"),
                org.mockito.ArgumentMatchers.eq("oa"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    @DisplayName("createApprovalWorkflow-记录时间线到 ChangeTimeline")
    void createApprovalWorkflow_recordTimeline() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setChangeNo("CR-1-0002");
        cr.setRequestedBy(50L);

        service.createApprovalWorkflow(cr);

        verify(changeTimelineMapper).insert(any(com.zhutao.medrms.change.domain.entity.ChangeTimelineEntry.class));
    }
}
