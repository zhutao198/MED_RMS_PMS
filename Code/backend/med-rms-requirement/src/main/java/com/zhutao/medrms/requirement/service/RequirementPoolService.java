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
    public String addToPool(String source, String sourceNo, String rawDescription, Long createdBy,
                          String title, String priority, String businessScenario, String competitiveAnalysis) {
        RequirementPool pool = new RequirementPool();
        pool.setId(generatePoolId());
        pool.setSource(source);
        pool.setSourceNo(sourceNo);
        pool.setRawDescription(rawDescription);
        pool.setTitle(title != null && !title.isBlank() ? title : extractTitle(rawDescription));
        pool.setPriority(priority);
        pool.setBusinessScenario(businessScenario);
        pool.setCompetitiveAnalysis(competitiveAnalysis);
        pool.setStatus("PENDING");
        Long effectiveCreatedBy = createdBy != null ? createdBy : SecurityUtils.getCurrentUserId();
        if (effectiveCreatedBy != null) {
            pool.setCreatedBy(effectiveCreatedBy);
        }
        pool.setCreatedAt(LocalDateTime.now());
        poolMapper.insert(pool);
        return pool.getId();
    }

    private synchronized String generatePoolId() {
        String todayPrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        LambdaQueryWrapper<RequirementPool> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(RequirementPool::getId, todayPrefix);
        wrapper.orderByDesc(RequirementPool::getId);
        wrapper.last("LIMIT 1");
        RequirementPool last = poolMapper.selectOne(wrapper);
        int seq = 1;
        if (last != null && last.getId() != null) {
            String seqStr = last.getId().substring(todayPrefix.length());
            try {
                seq = Integer.parseInt(seqStr) + 1;
            } catch (NumberFormatException ignored) {}
        }
        return todayPrefix + String.format("%03d", seq);
    }

    /**
     * 将收集池条目转换为 URS 正式需求
     */
    @Transactional
    public Requirement convertToUrs(String poolId, Long projectId, String priority) {
        if (poolId == null) throw BusinessException.param("poolId 不能为空");
        if (projectId == null) throw BusinessException.param("projectId 不能为空");
        if (priority == null || priority.isBlank()) throw BusinessException.param("priority 不能为空");

        RequirementPool pool = poolMapper.selectById(poolId);
        if (pool == null) {
            throw BusinessException.notFound("RP0101", "需求收集项不存在");
        }
        if (!"PENDING".equals(pool.getStatus())) {
            throw BusinessException.stateConflict("仅 PENDING 状态可转换（当前 " + pool.getStatus() + "）");
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

    private String extractTitle(String description) {
        if (description == null || description.isBlank()) return "未命名需求";
        return description.length() > 50 ? description.substring(0, 50) + "..." : description;
    }

    private String generateUrsNo(Long projectId) {
        long count = requirementMapper.selectCount(
            new LambdaQueryWrapper<Requirement>()
                .eq(Requirement::getProjectId, projectId)
                .eq(Requirement::getRequirementType, "URS")
        );
        return String.format("URS-%d-%03d", projectId, count + 1);
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
            addToPool(source, sourceNo, rawDescription, null, title, null, null, null);
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
        if (!"REJECTED".equals(pool.getStatus())) {
            throw BusinessException.stateConflict("仅 REJECTED 状态的条目可删除");
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