package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        // FR-0.17 前置校验：所有需求必须已审批
        for (Long reqId : requirementIds) {
            Requirement req = requirementMapper.selectById(reqId);
            if (req == null) {
                throw BusinessException.notFound("REQ0101", "需求不存在: id=" + reqId);
            }
            if (!RequirementStatus.APPROVED.equals(req.getStatus())) {
                throw BusinessException.stateConflict(
                    "存在未通过评审的需求 " + req.getRequirementNo() + "（FR-0.17 操作序列强制检查）");
            }
        }

        for (Long reqId : requirementIds) {
            Requirement requirement = requirementMapper.selectById(reqId);
            if (requirement != null) {
                requirement.setBaselineId(baselineId);
                requirement.setStatus(RequirementStatus.BASELINE);
                requirementMapper.updateById(requirement);
            }
        }

        // 更新基线快照数据
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
            for (Long reqId : requirementIds) {
                Requirement req = requirementMapper.selectById(reqId);
                if (req != null) {
                    allRequirements.add(req);
                }
            }
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

    @Transactional
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
        if (!"DRAFT".equals(baseline.getStatus())) {
            throw BusinessException.stateConflict("基线状态不允许锁定，必须是 DRAFT 状态，当前状态: " + baseline.getStatus());
        }

        baseline.setLockUser1Id(user1Id);
        baseline.setLockSignatureId1(signatureId1);
        baseline.setLockUser2Id(user2Id);
        baseline.setLockSignatureId2(signatureId2);
        baseline.setLockedBy(user1Id);
        baseline.setLockedAt(LocalDateTime.now());
        baseline.setStatus("LOCKED");
        baselineMapper.updateById(baseline);

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

        Baseline baseline = baselineMapper.selectById(baselineId);
        if (baseline == null) {
            throw BusinessException.notFound("RQ0101", "基线不存在");
        }
        if (!"LOCKED".equals(baseline.getStatus())) {
            throw BusinessException.stateConflict("基线状态不允许解锁，必须是 LOCKED 状态，当前状态: " + baseline.getStatus());
        }

        baseline.setStatus("DRAFT");
        baseline.setLockedBy(null);
        baseline.setLockedAt(null);
        baseline.setLockUser1Id(null);
        baseline.setLockSignatureId1(null);
        baseline.setLockUser2Id(null);
        baseline.setLockSignatureId2(null);
        baseline.setUpdatedAt(LocalDateTime.now());
        baselineMapper.updateById(baseline);

        return baseline;
    }

    public Baseline getById(Long id) {
        return baselineMapper.selectById(id);
    }

    public List<Baseline> getByProject(Long projectId) {
        return baselineMapper.selectList(
            new LambdaQueryWrapper<Baseline>()
                .eq(Baseline::getProjectId, projectId)
                .orderByDesc(Baseline::getCreatedAt)
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
