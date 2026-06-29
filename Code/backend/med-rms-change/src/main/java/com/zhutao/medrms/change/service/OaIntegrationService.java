package com.zhutao.medrms.change.service;

import com.zhutao.medrms.change.domain.entity.ChangeRequest;
import com.zhutao.medrms.change.domain.entity.ChangeTimelineEntry;
import com.zhutao.medrms.change.mapper.ChangeTimelineMapper;
import com.zhutao.medrms.common.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v1.47 BUG #120 P0 修复：OA 集成服务
 * 设计依据：chg-mgr-详细设计.md §3.1 / §6
 * <p>
 * 当前实现：mock 模式（生产可对接企业 OA 系统的 HTTP API）
 * <p>
 * 支持：
 * - MAJOR/EMERGENCY 变更自动推 OA 审批流
 * - NORMAL/DOCUMENT 变更走内部审批（不推 OA）
 * - OA 审批结果回写到变更状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OaIntegrationService {

    private final OutboxService outboxService;
    private final ChangeTimelineMapper changeTimelineMapper;

    /** OA 工作流 ID 缓存（生产可对接 OA 系统获取） */
    private final Map<String, String> oaWorkflowCache = new ConcurrentHashMap<>();

    /**
     * 创建 OA 审批工作流
     * @return OA workflowId（mock 模式生成 UUID）
     */
    public String createApprovalWorkflow(ChangeRequest change) {
        String workflowId = "OA-WF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        oaWorkflowCache.put(workflowId, change.getChangeNo());

        log.info("[OA] 创建审批工作流: workflowId={}, changeNo={}, changeType={}",
                workflowId, change.getChangeNo(), change.getChangeType());

        // 发 OA 领域事件
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("workflowId", workflowId);
            payload.put("changeNo", change.getChangeNo());
            payload.put("changeType", change.getChangeType());
            payload.put("approvers", "QA_MANAGER,PROJECT_MANAGER");
            outboxService.append("OAApprovalDispatched", "oa", change.getId(), payload);
        } catch (Exception e) {
            log.warn("发布 OA 事件失败: changeId={}, err={}", change.getId(), e.getMessage());
        }

        // 记录时间线
        recordTimeline(change.getId(), change.getRequestedBy(),
                "OA 审批工作流已创建，workflowId=" + workflowId);

        return workflowId;
    }

    /**
     * 接收 OA 审批回写（生产可由 OA 系统回调）
     */
    public void onOaApprovalCallback(String workflowId, String decision, String comments, Long operatorId) {
        String changeNo = oaWorkflowCache.get(workflowId);
        log.info("[OA] 收到审批回写: workflowId={}, changeNo={}, decision={}",
                workflowId, changeNo, decision);

        // 发 OA 回写领域事件
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("workflowId", workflowId);
            payload.put("changeNo", changeNo);
            payload.put("decision", decision);
            payload.put("operatorId", operatorId);
            outboxService.append("OAApprovalCallback", "oa", null, payload);
        } catch (Exception e) {
            log.warn("发布 OA 回写事件失败: workflowId={}, err={}", workflowId, e.getMessage());
        }
    }

    private void recordTimeline(Long changeId, Long operatorId, String details) {
        try {
            ChangeTimelineEntry entry = new ChangeTimelineEntry();
            entry.setChangeId(changeId);
            entry.setEvent(ChangeTimelineEntry.EVENT_OA_DISPATCHED);
            entry.setOperatorId(operatorId);
            entry.setDetails(details);
            changeTimelineMapper.insert(entry);
        } catch (Exception e) {
            log.warn("记录 OA 时间线失败: changeId={}, err={}", changeId, e.getMessage());
        }
    }
}
