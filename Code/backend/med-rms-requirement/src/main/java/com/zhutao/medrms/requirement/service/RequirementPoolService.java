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

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RequirementPoolService {

    private final RequirementPoolMapper poolMapper;
    private final RequirementMapper requirementMapper;

    /**
     * 添加需求到收集池
     * @return 新插入记录的 id
     */
    public Long addToPool(String source, String sourceNo, String rawDescription, Long createdBy) {
        RequirementPool pool = new RequirementPool();
        pool.setSource(source);
        pool.setSourceNo(sourceNo);
        pool.setRawDescription(rawDescription);
        pool.setTitle(extractTitle(rawDescription));
        pool.setStatus("PENDING");
        // 优先使用参数传入的 createdBy（兼容历史调用），否则从 SecurityContext 取
        Long effectiveCreatedBy = createdBy != null ? createdBy : SecurityUtils.getCurrentUserId();
        if (effectiveCreatedBy != null) {
            pool.setCreatedBy(effectiveCreatedBy);
        }
        pool.setCreatedAt(LocalDateTime.now());
        poolMapper.insert(pool);
        return pool.getId();
    }

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
}