package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.util.SecurityUtils;
import com.zhutao.medrms.compliance.domain.entity.SafetyClassification;
import com.zhutao.medrms.compliance.mapper.SafetyClassificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SafetyClassificationService {

    private final SafetyClassificationMapper mapper;

    @Transactional
    public SafetyClassification create(Long projectId, String safetyClass, String rationale, String remarks) {
        SafetyClassification sc = new SafetyClassification();
        sc.setProjectId(projectId);
        sc.setSafetyClass(safetyClass);
        sc.setClassificationRationale(rationale);
        sc.setRemarks(remarks);
        sc.setClassifiedBy(SecurityUtils.getCurrentUserId());
        sc.setStatus("DRAFT");
        sc.setCreatedAt(LocalDateTime.now());
        sc.setUpdatedAt(LocalDateTime.now());
        mapper.insert(sc);
        log.info("安全分类创建: id={}, projectId={}, class={}", sc.getId(), projectId, safetyClass);
        return sc;
    }

    /**
     * v1.47 BUG #124 P0 修复：双人签名锁定
     * 必须由不同用户复核（不能自己复核自己），复核后状态变 LOCKED
     */
    @Transactional
    public SafetyClassification review(Long id, Long reviewerId) {
        SafetyClassification sc = mapper.selectById(id);
        if (sc == null) throw BusinessException.notFound("CO0101", "安全分类不存在");
        if (!"DRAFT".equals(sc.getStatus()) && !"UNDER_REVIEW".equals(sc.getStatus())) {
            throw BusinessException.notFound("CO0102", "仅 DRAFT/UNDER_REVIEW 状态可复核");
        }
        if (sc.getClassifiedBy() != null && sc.getClassifiedBy().equals(reviewerId)) {
            throw BusinessException.notFound("CO0103", "复核人不能与分类人相同（双人签名锁定）");
        }
        sc.setReviewedBy(reviewerId);
        sc.setReviewedAt(LocalDateTime.now());
        sc.setStatus("LOCKED");
        sc.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(sc);
        log.info("[AUDIT][SAFETY_CLASS] review id={}, reviewerId={}", id, reviewerId);
        return sc;
    }

    public SafetyClassification getById(Long id) {
        SafetyClassification sc = mapper.selectById(id);
        if (sc == null) throw BusinessException.notFound("CO0101", "安全分类不存在");
        return sc;
    }

    public IPage<SafetyClassification> list(Long projectId, int page, int size) {
        Page<SafetyClassification> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<SafetyClassification> w = new LambdaQueryWrapper<>();
        if (projectId != null) w.eq(SafetyClassification::getProjectId, projectId);
        w.orderByDesc(SafetyClassification::getCreatedAt);
        return mapper.selectPage(pageObj, w);
    }
}
