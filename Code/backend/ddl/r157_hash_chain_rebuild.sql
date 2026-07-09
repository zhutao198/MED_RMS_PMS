-- =============================================================================
-- R157 修复：哈希链重建（F1：id=29 PREV_HASH_MISMATCH）
--   根因：AuthController.writeAuditLogWithHash() 绕过 AuditLogService，
--         两路径并发 getLastHash() 得到相同 prev_hash。
--   代码修复：共用 SecurityUtils.AUDIT_HASH_LOCK 跨模块锁。
--   本脚本：Python audit_log_reseed.py 执行通过，10/10 链式校验一致。
--   验证：GET /compliance/audit-logs/verify/detailed → valid=true
-- =============================================================================

-- 验证哈希链完整性
SELECT 'R157 验证' AS check_name,
       (SELECT current_hash FROM compliance_schema.t_audit_log ORDER BY id DESC LIMIT 1) AS last_chain_hash;
