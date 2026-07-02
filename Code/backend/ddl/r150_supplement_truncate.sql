-- =============================================================================
-- R150 补充清理：test_data_full_flow.sql 未覆盖的 esign / audit 表
-- 必要性：链路 A 电子签名 OTP 需要从干净起点验证 R148 修复的 insert 分支；
--         链路 B 哈希链需要从干净起点验证 R146/R147 修复。
-- 外键探查（2026-07-02）：t_signature_record / t_signature_intent /
--                         t_audit_log 均为 FK 端点（无被外键引用），可安全 TRUNCATE。
-- =============================================================================

-- 电子签名：清空签名记录和签名意图，重置自增序列
TRUNCATE TABLE esign_schema.t_signature_record RESTART IDENTITY CASCADE;
TRUNCATE TABLE esign_schema.t_signature_intent RESTART IDENTITY CASCADE;

-- 审计日志：清空全部 1200+ 历史日志，重置自增序列
TRUNCATE TABLE compliance_schema.t_audit_log RESTART IDENTITY CASCADE;
