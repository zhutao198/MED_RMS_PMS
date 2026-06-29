package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.util.DateUtils;
import com.zhutao.medrms.common.util.SecurityUtils;
import com.zhutao.medrms.compliance.domain.dto.HashChainVerifyResult;
import com.zhutao.medrms.compliance.domain.entity.AuditLog;
import com.zhutao.medrms.compliance.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    /**
     * 记录审计日志
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog recordAuditLog(String eventType, String entityType, Long entityId,
                                   Long operatorId, String operatorName, String operation,
                                   Object oldValue, Object newValue, String reason, String ipAddress) {
        // 获取上一条记录的哈希值
        String prevHash = getLastHash();

        // v1.45 BUG #93 修复：hash 用的 timestamp 必须与 createdAt 是同一时刻
        // 修复前：hash 用 T1 = nowIsoStr()，但 createdAt = LocalDateTime.now() 是 T2 ≠ T1，
        //         verifyHashChain() 用 createdAt 算的 hash 永远对不上原 hash。
        // 修复后：now 一次取，hash 与 createdAt 共用。
        LocalDateTime now = LocalDateTime.now();
        String timestamp = DateUtils.formatIso(now);
        // v1.45 BUG #93 修复：先 toJson 一次，hash 与存库都使用同一份 JSON 字符串（避免双重 JSON）
        String oldJson = toJson(oldValue);
        String newJson = toJson(newValue);
        String currentHash = SecurityUtils.calculateAuditHash(
                prevHash, eventType, entityType, entityId,
                operatorId, operation, oldJson, newJson, timestamp
        );

        AuditLog auditLog = new AuditLog();
        auditLog.setPrevHash(prevHash);
        auditLog.setCurrentHash(currentHash);
        auditLog.setEventType(eventType);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setOperatorId(operatorId);
        auditLog.setOperatorName(operatorName);
        auditLog.setOperation(operation);
        auditLog.setOldValue(oldJson);
        auditLog.setNewValue(newJson);
        auditLog.setReason(reason);
        auditLog.setIpAddress(ipAddress);
        auditLog.setCreatedAt(now);

        auditLogMapper.insert(auditLog);

        log.info("审计日志记录: id={}, event={}, entity={}/{}",
                auditLog.getId(), eventType, entityType, entityId);

        return auditLog;
    }

    /**
     * 校验哈希链完整性
     */
    public boolean verifyHashChain() {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(AuditLog::getId);
        List<AuditLog> logs = auditLogMapper.selectList(wrapper);

        if (logs.isEmpty()) {
            return true;
        }

        String expectedPrevHash = "0".repeat(64); // 创世记录的prev_hash
        for (AuditLog auditLog : logs) {
            // 验证prev_hash
            if (!auditLog.getPrevHash().equals(expectedPrevHash)) {
                log.error("哈希链断裂: id={}, expected prev_hash={}, actual={}",
                        auditLog.getId(), expectedPrevHash, auditLog.getPrevHash());
                return false;
            }

            // 验证current_hash
            String timestamp = DateUtils.formatIso(auditLog.getCreatedAt());
            // v1.45 BUG #93 修复：直接传存储的 JSON 字符串，不再二次 toJson
            String recalculatedHash = SecurityUtils.calculateAuditHash(
                    auditLog.getPrevHash(),
                    auditLog.getEventType(),
                    auditLog.getEntityType(),
                    auditLog.getEntityId(),
                    auditLog.getOperatorId(),
                    auditLog.getOperation(),
                    auditLog.getOldValue(),
                    auditLog.getNewValue(),
                    timestamp
            );

            if (!recalculatedHash.equals(auditLog.getCurrentHash())) {
                log.error("哈希值不匹配: id={}, expected={}, actual={}",
                        auditLog.getId(), recalculatedHash, auditLog.getCurrentHash());
                return false;
            }

            expectedPrevHash = auditLog.getCurrentHash();
        }

        log.info("哈希链校验通过，共 {} 条记录", logs.size());
        return true;
    }

    /**
     * 校验哈希链完整性并返回详细诊断 (v1.46 P0-后端-8)
     *
     * Why: 历史日志因多次算法迭代 (DDL 125 重命名 / BUG #93 时间戳) 存在断裂点。
     *   仅返回 boolean 无法定位问题，审计员需要看到首个断裂点 ID 才能决定
     *   是否重建链 (rebuild) 或保留原始证据。
     */
    public HashChainVerifyResult verifyHashChainDetailed() {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(AuditLog::getId);
        List<AuditLog> logs = auditLogMapper.selectList(wrapper);

        if (logs.isEmpty()) {
            return new HashChainVerifyResult(true, 0, null, null, null, "审计日志为空");
        }

        String expectedPrevHash = "0".repeat(64);
        Long lastValidId = null;
        for (AuditLog auditLog : logs) {
            if (!auditLog.getPrevHash().equals(expectedPrevHash)) {
                String msg = String.format("prev_hash 不匹配：记录 %d 的 prev_hash 与前一条 current_hash 不一致", auditLog.getId());
                log.error(msg);
                return new HashChainVerifyResult(false, logs.size(), auditLog.getId(), "PREV_HASH_MISMATCH", lastValidId, msg);
            }

            String timestamp = DateUtils.formatIso(auditLog.getCreatedAt());
            String recalculatedHash = SecurityUtils.calculateAuditHash(
                    auditLog.getPrevHash(),
                    auditLog.getEventType(),
                    auditLog.getEntityType(),
                    auditLog.getEntityId(),
                    auditLog.getOperatorId(),
                    auditLog.getOperation(),
                    auditLog.getOldValue(),
                    auditLog.getNewValue(),
                    timestamp
            );

            if (!recalculatedHash.equals(auditLog.getCurrentHash())) {
                String msg = String.format("current_hash 不匹配：记录 %d 的 stored hash 与按当前算法重算的 hash 不一致（最可能由历史算法迭代导致）", auditLog.getId());
                log.error(msg);
                return new HashChainVerifyResult(false, logs.size(), auditLog.getId(), "CURRENT_HASH_MISMATCH", lastValidId, msg);
            }

            lastValidId = auditLog.getId();
            expectedPrevHash = auditLog.getCurrentHash();
        }

        log.info("哈希链校验通过，共 {} 条记录", logs.size());
        return new HashChainVerifyResult(true, logs.size(), null, null, lastValidId, "全部通过");
    }

    /**
     * 从指定起始 ID 校验哈希链 (v1.46 P0-后端-8)
     *
     * Use case: 历史日志因算法迭代断裂（如 id <= 166 断裂于 167），
     *   审计员想知道 id=167 之后的链是否仍然完好。调用此方法时入参为断裂点下一条 ID。
     *
     * 起始 ID 的 prev_hash 不会被校验（因为它的前驱不在校验范围内），
     *   后续每条都按正常链式校验。
     */
    public HashChainVerifyResult verifyHashChainFrom(Long startId) {
        if (startId == null) {
            return verifyHashChainDetailed();
        }
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(AuditLog::getId, startId).orderByAsc(AuditLog::getId);
        List<AuditLog> logs = auditLogMapper.selectList(wrapper);

        if (logs.isEmpty()) {
            return new HashChainVerifyResult(true, 0, null, null, null, "无符合范围的记录");
        }

        Long lastValidId = null;
        int checked = 0;
        // 起始 ID 的 prev_hash 不在校验范围内（信任它作为锚点）
        // 但仍要校验起始 ID 自身的 current_hash 是否与字段一致
        for (int i = 0; i < logs.size(); i++) {
            AuditLog auditLog = logs.get(i);
            String timestamp = DateUtils.formatIso(auditLog.getCreatedAt());
            String recalculatedHash = SecurityUtils.calculateAuditHash(
                    auditLog.getPrevHash(),
                    auditLog.getEventType(),
                    auditLog.getEntityType(),
                    auditLog.getEntityId(),
                    auditLog.getOperatorId(),
                    auditLog.getOperation(),
                    auditLog.getOldValue(),
                    auditLog.getNewValue(),
                    timestamp
            );
            if (!recalculatedHash.equals(auditLog.getCurrentHash())) {
                String msg = String.format("current_hash 不匹配：记录 %d (fromId=%d)", auditLog.getId(), startId);
                log.error(msg);
                return new HashChainVerifyResult(false, logs.size(), auditLog.getId(), "CURRENT_HASH_MISMATCH", lastValidId, msg);
            }
            // 校验后续的 prev_hash 与前一条的 current_hash 匹配
            if (i > 0) {
                String expectedPrev = logs.get(i - 1).getCurrentHash();
                if (!auditLog.getPrevHash().equals(expectedPrev)) {
                    String msg = String.format("prev_hash 不匹配：记录 %d (fromId=%d) 的 prev_hash 与前一条 current_hash 不一致", auditLog.getId(), startId);
                    log.error(msg);
                    return new HashChainVerifyResult(false, logs.size(), auditLog.getId(), "PREV_HASH_MISMATCH", lastValidId, msg);
                }
            }
            lastValidId = auditLog.getId();
            checked++;
        }

        log.info("哈希链分段校验通过 (fromId={})，共 {} 条记录", startId, checked);
        return new HashChainVerifyResult(true, checked, null, null, lastValidId, "分段通过");
    }

    /**
     * 查询实体的审计日志
     */
    public List<AuditLog> getAuditLogsForEntity(String entityType, Long entityId) {
        return auditLogMapper.selectByEntity(entityType, entityId);
    }

    /**
     * 查询用户的操作日志
     */
    public List<AuditLog> getAuditLogsByOperator(Long operatorId) {
        return auditLogMapper.selectByOperator(operatorId);
    }

    /**
     * 查询时间范围内的审计日志
     */
    public List<AuditLog> getAuditLogsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return auditLogMapper.selectByTimeRange(startTime, endTime);
    }

    /**
     * 分页查询审计日志（支持多条件过滤）
     */
    public List<AuditLog> listAuditLogs(String eventType, String entityType, Long entityId,
                                        Long operatorId, LocalDateTime startTime, LocalDateTime endTime,
                                        int page, int size) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AuditLog::getCreatedAt);
        if (eventType != null && !eventType.isBlank()) {
            wrapper.eq(AuditLog::getEventType, eventType);
        }
        if (entityType != null && !entityType.isBlank()) {
            wrapper.eq(AuditLog::getEntityType, entityType);
        }
        if (entityId != null) {
            wrapper.eq(AuditLog::getEntityId, entityId);
        }
        if (operatorId != null) {
            wrapper.eq(AuditLog::getOperatorId, operatorId);
        }
        if (startTime != null && endTime != null) {
            wrapper.between(AuditLog::getCreatedAt, startTime, endTime);
        }
        wrapper.last("LIMIT " + size + " OFFSET " + (page * size));
        return auditLogMapper.selectList(wrapper);
    }

    /**
     * 导出审计日志为CSV
     */
    public List<AuditLog> getLogsForExport(LocalDateTime startTime, LocalDateTime endTime, String entityType) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (startTime != null && endTime != null) {
            wrapper.between(AuditLog::getCreatedAt, startTime, endTime);
        }
        if (entityType != null && !entityType.isBlank()) {
            wrapper.eq(AuditLog::getEntityType, entityType);
        }
        wrapper.orderByAsc(AuditLog::getCreatedAt);
        return auditLogMapper.selectList(wrapper);
    }

    /**
     * 生成CSV格式内容
     */
    public String generateCsv(List<AuditLog> logs) {
        StringBuilder csv = new StringBuilder();
        csv.append("ID,EventType,EntityType,EntityId,OperatorName,Operation,Reason,CreatedAt\n");
        for (AuditLog log : logs) {
            csv.append(String.format("%d,%s,%s,%d,%s,%s,%s,%s\n",
                    log.getId(),
                    log.getEventType() != null ? log.getEventType() : "",
                    log.getEntityType() != null ? log.getEntityType() : "",
                    log.getEntityId() != null ? log.getEntityId() : 0,
                    log.getOperatorName() != null ? log.getOperatorName() : "",
                    log.getOperation() != null ? log.getOperation() : "",
                    log.getReason() != null ? log.getReason().replace(",", ";") : "",
                    log.getCreatedAt() != null ? log.getCreatedAt().toString() : ""
            ));
        }
        return csv.toString();
    }

    private String getLastHash() {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        // B-01 Fix: 用 .limit(1) 代替 .last("LIMIT 1")
        // MyBatis-Plus 对 "LIMIT 1" (大写) 解析异常，会产生 OFFSET 1，
        // 导致查到的是第二条记录（OFFSET 1）而非第一条（limit 1），
        // 当最新记录 currHash=NULL 时，prevHash 永远为 NULL。
        wrapper.orderByDesc(AuditLog::getId).last("limit 1");
        AuditLog lastLog = auditLogMapper.selectOne(wrapper);
        return lastLog == null ? "0".repeat(64) : lastLog.getCurrentHash();
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        return com.alibaba.fastjson2.JSON.toJSONString(obj);
    }
}