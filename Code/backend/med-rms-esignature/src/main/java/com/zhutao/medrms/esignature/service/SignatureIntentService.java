package com.zhutao.medrms.esignature.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.esignature.domain.entity.SignatureIntent;
import com.zhutao.medrms.esignature.mapper.SignatureIntentMapper;
import com.zhutao.medrms.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
    private final NotificationService notificationService; // R219 分级通知
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
        // R218 v1.74：若查 PENDING，自动排除已过期（expiresAt < now）的脏数据
        if ("PENDING".equalsIgnoreCase(status)) {
            wrapper.ge(SignatureIntent::getExpiresAt, LocalDateTime.now());
        }
        wrapper.eq(status != null && !status.isBlank(), SignatureIntent::getStatus, status);
        // 优先按 id desc，避免相同 created_at 时分页不稳定
        wrapper.orderByDesc(SignatureIntent::getId);
        return intentMapper.selectPage(pageObj, wrapper);
    }

    /**
     * R218 v1.74: 定时任务 — 每分钟扫描 PENDING intent 把过期标记为 EXPIRED
     * 避免脏数据 + 保证审计可追溯（status 字段反映真实状态）
     */
    @Scheduled(fixedRate = 60000) // 每分钟
    public void sweepExpiredIntents() {
        try {
            int updated = intentMapper.update(null,
                new LambdaUpdateWrapper<SignatureIntent>()
                    .eq(SignatureIntent::getStatus, SignatureIntent.STATUS_PENDING)
                    .lt(SignatureIntent::getExpiresAt, LocalDateTime.now())
                    .set(SignatureIntent::getStatus, SignatureIntent.STATUS_EXPIRED));
            if (updated > 0) {
                log.info("R218 扫描标记过期 intent: count={}", updated);
            }
        } catch (Exception e) {
            log.warn("R218 扫描过期 intent 失败: {}", e.getMessage());
        }
    }

    /**
     * R219 v1.75: 分级通知定时任务 — 每分钟扫 PENDING intent 触发 T-5/T-1/T+0 通知
     * 幂等保证：通过 notified_*_at 字段避免重复通知
     */
    @Scheduled(fixedRate = 60000)
    public void sweepExpiredNotifications() {
        try {
            LocalDateTime now = LocalDateTime.now();
            // T-5min：expiresAt 5min 内还未发过 5min 通知
            // T-1min：expiresAt 1min 内还未发过 1min 通知
            // T+0min：已过期还未发过 expired 通知
            List<SignatureIntent> pendingList = intentMapper.selectList(
                new LambdaQueryWrapper<SignatureIntent>()
                    .eq(SignatureIntent::getStatus, SignatureIntent.STATUS_PENDING));
            int t5Count = 0, t1Count = 0, t0Count = 0;
            for (SignatureIntent intent : pendingList) {
                if (intent.getExpiresAt() == null) continue;
                long secondsLeft = java.time.Duration.between(now, intent.getExpiresAt()).getSeconds();
                // T+0min（已过期）
                if (secondsLeft <= 0 && intent.getNotifiedExpiredAt() == null) {
                    sendExpiredNotification(intent);
                    intent.setNotifiedExpiredAt(now);
                    intentMapper.updateById(intent);
                    t0Count++;
                    continue;
                }
                // T-1min（≤60s 且 > 0）
                if (secondsLeft <= 60 && intent.getNotified1minAt() == null) {
                    sendUrgentNotification(intent);
                    intent.setNotified1minAt(now);
                    intentMapper.updateById(intent);
                    t1Count++;
                }
                // T-5min（≤300s 且 > 60s）
                if (secondsLeft <= 300 && secondsLeft > 60 && intent.getNotified5minAt() == null) {
                    sendReminderNotification(intent);
                    intent.setNotified5minAt(now);
                    intentMapper.updateById(intent);
                    t5Count++;
                }
            }
            if (t5Count + t1Count + t0Count > 0) {
                log.info("R219 分级通知: T-5={}, T-1={}, T+0={}", t5Count, t1Count, t0Count);
            }
        } catch (Exception e) {
            log.warn("R219 分级通知失败: {}", e.getMessage());
        }
    }

    private void sendReminderNotification(SignatureIntent intent) {
        try {
            String title = "⏰ 签名提醒：5 分钟后过期";
            String content = String.format("您有一条签名意图（%s）将在 5 分钟后过期，请尽快处理。", intent.getIntentNo());
            notificationService.sendSystemNotification(
                intent.getRequesterId(), title, content, "SIGNATURE_INTENT_REMIND", intent.getId());
        } catch (Exception e) {
            log.warn("R219 发送 T-5 通知失败: {}", e.getMessage());
        }
    }

    private void sendUrgentNotification(SignatureIntent intent) {
        try {
            String title = "🚨 签名紧急：1 分钟后过期";
            String content = String.format("签名意图 %s 即将过期（1 分钟内），请立即处理。", intent.getIntentNo());
            notificationService.sendSystemNotification(
                intent.getRequesterId(), title, content, "SIGNATURE_INTENT_URGENT", intent.getId());
        } catch (Exception e) {
            log.warn("R219 发送 T-1 通知失败: {}", e.getMessage());
        }
    }

    private void sendExpiredNotification(SignatureIntent intent) {
        try {
            String title = "❌ 签名已过期";
            String content = String.format("签名意图 %s 已过期。可点击「重新发起」创建新签名意图。",
                    intent.getIntentNo());
            notificationService.sendSystemNotification(
                intent.getRequesterId(), title, content, "SIGNATURE_INTENT_EXPIRED", intent.getId());
        } catch (Exception e) {
            log.warn("R219 发送 T+0 通知失败: {}", e.getMessage());
        }
    }

    /**
     * R219: 重新发起签名意图（基于已过期 intent 创建新 PENDING intent）
     * - 保留原 EXPIRED intent（审计追溯）
     * - 返回新 intent（用于立即跳转签署页）
     */
    @Transactional
    public SignatureIntent reissue(Long expiredIntentId) {
        SignatureIntent old = getById(expiredIntentId);
        if (!SignatureIntent.STATUS_EXPIRED.equals(old.getStatus())) {
            throw BusinessException.stateConflict(
                "仅 EXPIRED 状态的签名意图可重新发起（当前: " + old.getStatus() + "）");
        }
        // 用原 intent 的参数创建新 intent（除状态/过期时间/通知时间戳）
        return createIntent(old.getRequesterId(), old.getDocumentType(),
                old.getDocumentId(), old.getIntentCode(), old.getMeaningCode());
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
