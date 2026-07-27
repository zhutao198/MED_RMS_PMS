package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.zhutao.medrms.common.annotation.AuditLog;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.compliance.domain.entity.Baseline;
import com.zhutao.medrms.compliance.mapper.BaselineMapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.domain.entity.RequirementStatus;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * v1.48 P0 #2 修复：基线服务从 med-rms-requirement 迁移到 med-rms-compliance
 * 原因：基线是合规管理的重要概念（21 CFR Part 11 §11.10 封闭系统控制），
 *       应归属于合规域而非需求域，对齐 compliance-详细设计.md §1 类图
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BaselineService {

    private final BaselineMapper baselineMapper;
    private final RequirementMapper requirementMapper;

    /**
     * v1.48 P0 #2 修复：从 RequirementService 迁入
     * FR-0.17：所有纳入基线的需求必须已通过评审（APPROVED 状态）
     */
    @Transactional
    public void baselineRequirements(Long baselineId, List<Long> requirementIds) {
        // R232.2 DATA-026：N+1 优化 — 用 selectBatchIds 一次性加载所有需求
        if (requirementIds == null || requirementIds.isEmpty()) {
            log.warn("基线化需求列表为空: baselineId={}", baselineId);
            return;
        }
        List<Requirement> requirements = requirementMapper.selectBatchIds(requirementIds);

        // FR-0.17 前置校验：所有需求必须已审批
        for (Requirement req : requirements) {
            if (req == null) {
                throw BusinessException.notFound("REQ0101", "需求不存在");
            }
            if (!RequirementStatus.APPROVED.equals(req.getStatus())) {
                throw BusinessException.stateConflict(
                    "存在未通过评审的需求 " + req.getRequirementNo() + "（FR-0.17 操作序列强制检查）");
            }
        }

        // 批量更新需求状态为 BASELINE
        for (Requirement requirement : requirements) {
            requirement.setBaselineId(baselineId);
            requirement.setStatus(RequirementStatus.BASELINE);
            requirementMapper.updateById(requirement);
        }

        // 更新基线快照数据（N+1 优化：复用上面已加载的 requirements 列表）
        Baseline baseline = baselineMapper.selectById(baselineId);
        if (baseline != null) {
            String snapshotData = baseline.getSnapshotData();
            List<Requirement> allRequirements = new ArrayList<>();
            if (snapshotData != null) {
                try {
                    allRequirements = JSON.parseArray(snapshotData, Requirement.class);
                } catch (Exception e) {
                    allRequirements = new ArrayList<>();
                }
            }
            allRequirements.addAll(requirements);  // 直接复用，不再 selectById
            baseline.setSnapshotData(JSON.toJSONString(allRequirements));
            baselineMapper.updateById(baseline);
        }

        log.info("基线化需求完成: baselineId={}, count={}", baselineId, requirementIds.size());
    }

    @Transactional
    public Baseline createBaseline(Long projectId, String name, List<Long> requirementIds) {
        List<Requirement> requirements = requirementIds == null || requirementIds.isEmpty()
            ? new ArrayList<>()
            : requirementMapper.selectBatchIds(requirementIds);
        String snapshot = JSON.toJSONString(requirements);

        long count = baselineMapper.selectCount(null);
        String baselineNo = String.format("BL-%d-%04d", projectId, count + 1);

        Baseline baseline = new Baseline();
        baseline.setProjectId(projectId);
        baseline.setBaselineNo(baselineNo);
        baseline.setBaselineName(name);
        baseline.setBaselineType("REQUIREMENT");
        baseline.setSnapshotData(snapshot);
        baseline.setStatus("DRAFT");
        baseline.setCreatedAt(LocalDateTime.now());

        baselineMapper.insert(baseline);
        return baseline;
    }

    /**
     * R155 修复：双签锁定基线触发 @AuditLog 写审计日志（21 CFR Part 11 §11.200 合规关键事件）
     * entityIdSpel 用 #p0（第一个参数 baselineId）— 规避 -parameters 编译配置依赖
     */
    @Transactional
    @AuditLog(eventType = "STATUS_CHANGE", entityType = "BASELINE",
              operation = "双签锁定基线", entityIdSpel = "#p0")
    public Baseline lockBaseline(Long baselineId, Long user1Id, Long signatureId1, Long user2Id, Long signatureId2) {
        if (user1Id == null || user2Id == null) {
            throw BusinessException.param("基线锁定需 2 个不同签署人 user1Id 和 user2Id");
        }
        if (signatureId1 == null || signatureId2 == null) {
            throw BusinessException.param("基线锁定需 2 个不同的电子签名 ID（Part 11 §11.200 双签控制）");
        }
        if (user1Id.equals(user2Id)) {
            throw BusinessException.param("基线锁定 user1Id 与 user2Id 必须不同（双签控制 Part 11 §11.200）");
        }
        if (signatureId1.equals(signatureId2)) {
            throw BusinessException.param("基线锁定 signatureId1 与 signatureId2 必须不同");
        }

        Baseline baseline = baselineMapper.selectById(baselineId);
        if (baseline == null) {
            throw BusinessException.notFound("RQ0101", "基线不存在");
        }

        // R198-4 修复：原子 UPDATE 替代 read-then-write（防止并发竞态，300 并发锁同 baseline 时只有 1 次成功）
        LocalDateTime now = LocalDateTime.now();
        int updated = baselineMapper.update(null,
            new UpdateWrapper<Baseline>()
                .eq("id", baselineId)
                .eq("status", "DRAFT")
                .set("status", "LOCKED")
                .set("lock_user1_id", user1Id)
                .set("lock_signature_id1", signatureId1)
                .set("lock_user2_id", user2Id)
                .set("lock_signature_id2", signatureId2)
                .set("locked_by", user1Id)
                .set("locked_at", now)
        );
        if (updated == 0) {
            String currentStatus = baselineMapper.selectById(baselineId).getStatus();
            throw BusinessException.stateConflict("基线状态不允许锁定，必须是 DRAFT 状态，当前状态: " + currentStatus);
        }

        baseline.setStatus("LOCKED");
        baseline.setLockUser1Id(user1Id);
        baseline.setLockSignatureId1(signatureId1);
        baseline.setLockUser2Id(user2Id);
        baseline.setLockSignatureId2(signatureId2);
        baseline.setLockedBy(user1Id);
        baseline.setLockedAt(now);
        return baseline;
    }

    @Transactional
    public Baseline unlockBaseline(Long baselineId, Long user1Id, Long signatureId1, Long user2Id, Long signatureId2, String reason) {
        if (user1Id == null || user2Id == null) {
            throw BusinessException.param("基线解锁需 2 个不同签署人 user1Id 和 user2Id");
        }
        if (signatureId1 == null || signatureId2 == null) {
            throw BusinessException.param("基线解锁需 2 个不同的电子签名 ID（Part 11 §11.200 双签控制）");
        }
        if (user1Id.equals(user2Id)) {
            throw BusinessException.param("基线解锁 user1Id 与 user2Id 必须不同（双签控制 Part 11 §11.200）");
        }
        if (signatureId1.equals(signatureId2)) {
            throw BusinessException.param("基线解锁 signatureId1 与 signatureId2 必须不同");
        }

        // R226.3 DATA-019：原子 UPDATE 替代 read-then-write（与 lockBaseline 配套）
        LocalDateTime now = LocalDateTime.now();
        int updated = baselineMapper.update(null,
            new UpdateWrapper<Baseline>()
                .eq("id", baselineId)
                .eq("status", "LOCKED")
                .set("status", "DRAFT")
                .set("locked_by", null)
                .set("locked_at", null)
                .set("lock_user1_id", null)
                .set("lock_signature_id1", null)
                .set("lock_user2_id", null)
                .set("lock_signature_id2", null)
                .set("updated_at", now)
        );
        if (updated == 0) {
            Baseline current = baselineMapper.selectById(baselineId);
            if (current == null) {
                throw BusinessException.notFound("RQ0101", "基线不存在");
            }
            throw BusinessException.stateConflict("基线状态不允许解锁，必须是 LOCKED 状态，当前状态: " + current.getStatus());
        }
        return baselineMapper.selectById(baselineId);
    }

    public Baseline getById(Long id) {
        return baselineMapper.selectById(id);
    }

    public List<Baseline> getByProject(Long projectId) {
        return baselineMapper.selectList(
            new QueryWrapper<Baseline>()
                .eq("project_id", projectId)
                .orderByDesc("created_at")
        );
    }

    public List<Baseline> listAll() {
        return baselineMapper.selectList(
            new QueryWrapper<Baseline>()
                .orderByDesc("created_at")
        );
    }

    public Map<String, Object> compare(Long baselineId1, Long baselineId2) {
        Baseline b1 = baselineMapper.selectById(baselineId1);
        Baseline b2 = baselineMapper.selectById(baselineId2);

        if (b1 == null || b2 == null) {
            throw BusinessException.notFound("RQ0101", "基线不存在");
        }

        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> removed = new ArrayList<>();
        List<Map<String, Object>> modified = new ArrayList<>();

        int count1 = 0;
        int count2 = 0;
        try {
            if (b1.getSnapshotData() != null) count1 = JSON.parseArray(b1.getSnapshotData()).size();
            if (b2.getSnapshotData() != null) count2 = JSON.parseArray(b2.getSnapshotData()).size();
        } catch (Exception e) {
            // ignore parse errors
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("addedCount", Math.max(0, count2 - count1));
        summary.put("removedCount", Math.max(0, count1 - count2));
        summary.put("modifiedCount", 0);

        Map<String, Object> result = new HashMap<>();
        result.put("baseline1Name", b1.getBaselineName());
        result.put("baseline2Name", b2.getBaselineName());
        result.put("added", added);
        result.put("removed", removed);
        result.put("modified", modified);
        result.put("summary", summary);

        return result;
    }
}
