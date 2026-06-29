package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.util.SecurityUtils;
import com.zhutao.medrms.compliance.domain.entity.PrCorrection;
import com.zhutao.medrms.compliance.mapper.PrCorrectionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrCorrectionService {

    private final PrCorrectionMapper mapper;

    @Transactional
    public PrCorrection create(Long problemReportId, String action, Long ownerId, LocalDateTime dueDate) {
        PrCorrection c = new PrCorrection();
        c.setProblemReportId(problemReportId);
        c.setAction(action);
        c.setOwnerId(ownerId);
        c.setDueDate(dueDate);
        c.setStatus(PrCorrection.STATUS_OPEN);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        mapper.insert(c);
        log.info("PR 纠正措施创建: id={}, problemReportId={}", c.getId(), problemReportId);
        return c;
    }

    @Transactional
    public PrCorrection complete(Long id, String effectiveness) {
        PrCorrection c = mapper.selectById(id);
        if (c == null) throw BusinessException.notFound("CO0201", "纠正措施不存在");
        c.setStatus(PrCorrection.STATUS_IN_PROGRESS);
        c.setCompletedAt(LocalDateTime.now());
        c.setEffectiveness(effectiveness);
        c.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(c);
        return c;
    }

    /**
     * v1.47 BUG #124 P0 修复：双人签名锁定（验证人不能是 owner）
     */
    @Transactional
    public PrCorrection verify(Long id, Long verifierId) {
        PrCorrection c = mapper.selectById(id);
        if (c == null) throw BusinessException.notFound("CO0201", "纠正措施不存在");
        if (c.getOwnerId() != null && c.getOwnerId().equals(verifierId)) {
            throw BusinessException.notFound("CO0202", "验证人不能与责任人相同（双人签名锁定）");
        }
        c.setVerifiedBy(verifierId);
        c.setVerifiedAt(LocalDateTime.now());
        c.setStatus(PrCorrection.STATUS_VERIFIED);
        c.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(c);
        log.info("[AUDIT][PR_CORRECTION] verify id={}, verifierId={}", id, verifierId);
        return c;
    }

    public PrCorrection getById(Long id) {
        PrCorrection c = mapper.selectById(id);
        if (c == null) throw BusinessException.notFound("CO0201", "纠正措施不存在");
        return c;
    }

    public IPage<PrCorrection> listByReport(Long problemReportId, int page, int size) {
        Page<PrCorrection> p = new Page<>(page, size);
        LambdaQueryWrapper<PrCorrection> w = new LambdaQueryWrapper<>();
        w.eq(PrCorrection::getProblemReportId, problemReportId)
                .orderByDesc(PrCorrection::getCreatedAt);
        return mapper.selectPage(p, w);
    }
}
