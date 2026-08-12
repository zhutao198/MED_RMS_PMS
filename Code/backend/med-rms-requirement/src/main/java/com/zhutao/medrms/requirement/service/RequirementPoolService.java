package com.zhutao.medrms.requirement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.util.SecurityUtils;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.domain.entity.RequirementPool;
import com.zhutao.medrms.requirement.mapper.RequirementPoolMapper;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RequirementPoolService {

    private final RequirementPoolMapper poolMapper;
    private final RequirementMapper requirementMapper;

    /**
     * 添加需求到收集池
     * @return 新插入记录的 id
     */
    public Long addToPool(String source, String sourceNo, String rawDescription, Long createdBy,
                          String title, String priority, String businessScenario, String competitiveAnalysis,
                          String proposer) {
        RequirementPool pool = new RequirementPool();
        // R201 修复：删 generatePoolId() 业务 ID，id 由 DB nextval 自增（bigint 列）
        // 原因：DB id 列是 bigint，但 entity 之前用 String 类型，导致 insert 时类型不匹配 → SY0000
        pool.setSource(source);
        pool.setSourceNo(sourceNo);
        pool.setRawDescription(rawDescription);
        pool.setTitle(title != null && !title.isBlank() ? title : extractTitle(rawDescription));
        pool.setPriority(priority);
        pool.setBusinessScenario(businessScenario);
        pool.setCompetitiveAnalysis(competitiveAnalysis);
        pool.setProposer(proposer);
        pool.setStatus("PENDING");
        Long effectiveCreatedBy = createdBy != null ? createdBy : SecurityUtils.getCurrentUserId();
        if (effectiveCreatedBy != null) {
            pool.setCreatedBy(effectiveCreatedBy);
        }
        pool.setCreatedAt(LocalDateTime.now());
        poolMapper.insert(pool);
        return pool.getId();
    }

    // R201 修复：删除 generatePoolId() — id 由 DB nextval 自动生成（bigint）
    // 历史遗留：原设计用 String "202607210001" 业务 ID，但 DB 列已改为 bigint 类型

    /**
     * 将收集池条目转换为 URS 正式需求
     */
    @Transactional
    public Requirement convertToUrs(Long poolId, Long projectId, String priority) {
        if (poolId == null) throw BusinessException.param("poolId 不能为空");
        if (projectId == null) throw BusinessException.param("projectId 不能为空");
        if (priority == null || priority.isBlank()) throw BusinessException.param("priority 不能为空");

        RequirementPool pool = poolMapper.selectById(poolId);
        if (pool == null) {
            throw BusinessException.notFound("RP0101", "需求收集项不存在");
        }
        // 差异 #5：PRD 收集池状态机 PENDING→PARSED→CONVERTED。
        // PARSED 为解析中间态（解析后进入），允许从 PENDING 或 PARSED 转入 URS；
        // 已 CONVERTED/REJECTED 不可重复转换。
        if (!"PENDING".equals(pool.getStatus()) && !"PARSED".equals(pool.getStatus())) {
            throw BusinessException.stateConflict("仅 PENDING/PARSED 状态可转换（当前 " + pool.getStatus() + "）");
        }

        Requirement urs = new Requirement();
        urs.setRequirementNo(generateUrsNo(projectId));
        urs.setRequirementType("URS");
        urs.setTitle(pool.getTitle());
        urs.setDescription(pool.getParsedDescription() != null ? pool.getParsedDescription() : pool.getRawDescription());
        urs.setPriority(priority);
        urs.setStatus("Draft");
        urs.setProjectId(projectId);
        // 从 SecurityContext 注入 createdBy/updatedBy（与 v1.39 资源管理 createdBy 修复同根因）
        Long currentUserId = SecurityUtils.getCurrentUserId();
        LocalDateTime nowDt = LocalDateTime.now();
        urs.setCreatedAt(nowDt);
        urs.setUpdatedAt(nowDt);
        if (currentUserId != null) {
            urs.setCreatedBy(currentUserId);
            urs.setUpdatedBy(currentUserId);
        }

        requirementMapper.insert(urs);

        pool.setStatus("CONVERTED");
        pool.setConvertedToId(urs.getId());
        poolMapper.updateById(pool);

        return urs;
    }

    /**
     * 差异 #5：将收集池条目标记为 PARSED（解析完成的中间态，PRD 状态机 PENDING→PARSED→CONVERTED）。
     * 解析后通常写入 parsedDescription，由调用方在 item 中一并更新；此处仅做状态迁移与解析时间记录。
     *
     * @param poolId 收集池条目 ID
     */
    @Transactional
    public RequirementPool parsePoolItem(Long poolId) {
        RequirementPool pool = poolMapper.selectById(poolId);
        if (pool == null) {
            throw BusinessException.notFound("RP0101", "需求收集项不存在");
        }
        if (!"PENDING".equals(pool.getStatus())) {
            throw BusinessException.stateConflict("仅 PENDING 可解析（当前 " + pool.getStatus() + "）");
        }
        pool.setStatus("PARSED");
        pool.setParsedAt(LocalDateTime.now());
        poolMapper.updateById(pool);
        return pool;
    }

    private String extractTitle(String description) {
        if (description == null || description.isBlank()) return "未命名需求";
        return description.length() > 50 ? description.substring(0, 50) + "..." : description;
    }

    private String generateUrsNo(Long projectId) {
        // R205 修复：原 count(*)+1 在并发/历史脏数据下会撞名（如 8333 已有 URS-101-010 但 count=9）
        // 改用 MAX(seq) + 1：永远基于实际最大序号递增，避免空位/重复
        String prefix = String.format("URS-%d-", projectId);
        Requirement last = requirementMapper.selectOne(
            new LambdaQueryWrapper<Requirement>()
                .likeRight(Requirement::getRequirementNo, prefix)
                .orderByDesc(Requirement::getId)
                .last("LIMIT 1"));
        int seq = 0;
        if (last != null && last.getRequirementNo() != null) {
            String lastNo = last.getRequirementNo();
            try {
                seq = Integer.parseInt(lastNo.substring(prefix.length()));
            } catch (NumberFormatException ignored) {}
        }
        return prefix + String.format("%03d", seq + 1);
    }

    /**
     * 从外部列表批量导入需求收集池（支持 JSON/Excel 导入）
     */
    @Transactional
    public int importFromList(List<Map<String, Object>> items) {
        int count = 0;
        for (Map<String, Object> item : items) {
            String rawDescription = tryGet(item, "rawDescription", "raw_description", "rawdescription", "原始描述", "描述", "内容");
            if (rawDescription == null || rawDescription.isBlank()) {
                continue;
            }
            String source = tryGet(item, "source", "来源", "INTERNAL");
            String sourceNo = tryGet(item, "sourceNo", "source_no", "sourceno", "来源编号", "编码");
            if (source == null) source = "INTERNAL";
            String title = tryGet(item, "title", "标题");
            String proposer = tryGet(item, "proposer", "提出人");
            addToPool(source, sourceNo, rawDescription, null, title, null, null, null, proposer);
            count++;
        }
        return count;
    }

    /**
     * 拒绝需求池条目（标记为 REJECTED）
     */
    public void rejectPoolItem(String id, String reason) {
        RequirementPool pool = poolMapper.selectById(id);
        if (pool == null) {
            throw BusinessException.notFound("RP0101", "需求收集项不存在");
        }
        if ("CONVERTED".equals(pool.getStatus())) {
            throw BusinessException.stateConflict("已转换的条目不可拒绝");
        }
        // 差异 #5：PARSED 为解析中间态，尚未转换，允许拒绝
        if ("PARSED".equals(pool.getStatus())) {
            pool.setStatus("REJECTED");
            pool.setRejectionReason(reason);
            poolMapper.updateById(pool);
            return;
        }
        pool.setStatus("REJECTED");
        pool.setRejectionReason(reason);
        poolMapper.updateById(pool);
    }

    /**
     * 删除需求池条目（物理删除，仅限 PENDING/REJECTED 状态）
     */
    public void deletePoolItem(String id) {
        RequirementPool pool = poolMapper.selectById(id);
        if (pool == null) {
            throw BusinessException.notFound("RP0101", "需求收集项不存在");
        }
        // 差异 #5：删除支持 REJECTED/PARSED 态（已转换或解析后未转换的草稿均可清理）
        if (!"REJECTED".equals(pool.getStatus()) && !"PARSED".equals(pool.getStatus())) {
            throw BusinessException.stateConflict("仅 REJECTED/PARSED 状态的条目可删除");
        }
        poolMapper.deleteById(id);
    }

    private String tryGet(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object v = map.get(key);
            if (v instanceof String s && !s.isBlank()) return s;
            if (v != null) return v.toString();
        }
        return null;
    }
}