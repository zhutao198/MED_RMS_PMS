package com.zhutao.medrms.esignature.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.esignature.domain.entity.SignatureIntent;
import com.zhutao.medrms.esignature.mapper.SignatureIntentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * v1.46 BUG #104 修复：签名意图服务。
 * 签名前必须先 createIntent，签名时校验 intentId（未过期 + requesterId 匹配）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignatureIntentService {

    private final SignatureIntentMapper intentMapper;
    private static final int DEFAULT_EXPIRY_MINUTES = 15;

    @Transactional
    public SignatureIntent createIntent(Long requesterId, String documentType, Long documentId,
                                        String intentCode, String meaningCode) {
        SignatureIntent intent = new SignatureIntent();
        intent.setIntentNo("INT-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4));
        intent.setRequesterId(requesterId);
        intent.setDocumentType(documentType);
        intent.setDocumentId(documentId);
        intent.setIntentCode(intentCode);
        intent.setMeaningCode(meaningCode != null ? meaningCode : intentCode);
        intent.setStatus(SignatureIntent.STATUS_PENDING);
        intent.setExpiresAt(LocalDateTime.now().plusMinutes(DEFAULT_EXPIRY_MINUTES));
        intent.setCreatedAt(LocalDateTime.now());
        // v1.47 BUG #139 P0 修复：使用自定义 INSERT 避免 MyBatis-Plus 自动生成漏列
        intentMapper.insertIntent(intent);
        log.info("签名意图创建: id={}, requesterId={}, doc={}/{}, expiresAt={}",
                intent.getId(), requesterId, documentType, documentId, intent.getExpiresAt());
        return intent;
    }

    public SignatureIntent validateAndConsume(Long intentId, Long signerId) {
        SignatureIntent intent = intentMapper.selectById(intentId);
        if (intent == null) {
            throw BusinessException.notFound("SG0101", "签名意图不存在");
        }
        if (!SignatureIntent.STATUS_PENDING.equals(intent.getStatus())) {
            throw BusinessException.notFound("SG0105", "签名意图已" + intent.getStatus() + "，不可再签");
        }
        if (intent.isExpired()) {
            intent.setStatus(SignatureIntent.STATUS_EXPIRED);
            intentMapper.updateById(intent);
            throw BusinessException.notFound("SG0106", "签名意图已过期");
        }
        if (!intent.getRequesterId().equals(signerId)) {
            throw BusinessException.notFound("SG0107", "签名意图申请人与签名人不一致");
        }
        return intent;
    }

    @Transactional
    public void markConsumed(Long intentId, Long signatureId, Long signerId) {
        SignatureIntent intent = intentMapper.selectById(intentId);
        if (intent == null) return;
        intent.setStatus(SignatureIntent.STATUS_CONSUMED);
        intent.setConsumedAt(LocalDateTime.now());
        intent.setConsumedBy(signerId);
        intent.setSignatureId(signatureId);
        intentMapper.updateById(intent);
    }

    @Transactional
    public void cancelIntent(Long intentId, Long operatorId) {
        SignatureIntent intent = intentMapper.selectById(intentId);
        if (intent == null) return;
        if (!SignatureIntent.STATUS_PENDING.equals(intent.getStatus())) {
            log.warn("签名意图状态非 PENDING，不取消: id={}, status={}", intentId, intent.getStatus());
            return;
        }
        if (!intent.getRequesterId().equals(operatorId)) {
            throw BusinessException.notFound("SG0108", "仅意向申请人可取消");
        }
        intent.setStatus(SignatureIntent.STATUS_CANCELLED);
        intentMapper.updateById(intent);
    }

    /**
     * R97 新增：按 signerId(意向申请人) + status 过滤分页查询签名意图。
     * <p>用途：Dashboard "待签字"计数 / SignatureList 待签字列表。<br>
     * 注意：Dashboard 语义是"谁需要签" = requesterId（签名前 intent.requesterId == signerId，签名后消费不变），所以这里按 requesterId 过滤。
     * <p>为何不加 .eq(field, null) 守护：MyBatis-Plus 的 eq(field, null) 会生成 WHERE field = null 永远 0 行
     * （参考 R90 BUG），必须用条件包裹式 eq(projectId != null, ...)。
     *
     * @param signerId 意向申请人 ID（null = 不限）
     * @param status   状态过滤（PENDING/CONSUMED/EXPIRED/CANCELLED；null/blank = 不限）
     * @param page     页码（0-based）
     * @param size     每页大小
     */
    public IPage<SignatureIntent> listIntents(Long signerId, String status, int page, int size) {
        Page<SignatureIntent> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<SignatureIntent> wrapper = new LambdaQueryWrapper<>();
        // R97：条件包裹式 eq 避免 R90 同类 bug（WHERE field = null 永假）
        wrapper.eq(signerId != null, SignatureIntent::getRequesterId, signerId);
        wrapper.eq(status != null && !status.isBlank(), SignatureIntent::getStatus, status);
        // 优先按 id desc，避免相同 created_at 时分页不稳定
        wrapper.orderByDesc(SignatureIntent::getId);
        return intentMapper.selectPage(pageObj, wrapper);
    }

    /**
     * R103 A1 新增：按 ID 查询签名意图详情（前端 SignatureIntentDetail.vue 用）
     * @param intentId 意图 ID
     * @return SignatureIntent；不存在抛 SY0301
     */
    public SignatureIntent getById(Long intentId) {
        SignatureIntent intent = intentMapper.selectById(intentId);
        if (intent == null) {
            throw BusinessException.notFound("SIG0301", "签名意图不存在: id=" + intentId);
        }
        return intent;
    }
}
