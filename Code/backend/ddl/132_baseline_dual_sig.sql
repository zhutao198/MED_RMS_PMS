-- DDL 132: 基线双签锁定字段（v1.47 P0 修复 BUG #119）
-- 设计依据：compliance-详细设计.md §3.2 双人签名锁定

ALTER TABLE compliance_schema.t_baseline ADD COLUMN IF NOT EXISTS lock_user1_id BIGINT;
ALTER TABLE compliance_schema.t_baseline ADD COLUMN IF NOT EXISTS lock_signature_id1 BIGINT;
ALTER TABLE compliance_schema.t_baseline ADD COLUMN IF NOT EXISTS lock_user2_id BIGINT;
ALTER TABLE compliance_schema.t_baseline ADD COLUMN IF NOT EXISTS lock_signature_id2 BIGINT;

COMMENT ON COLUMN compliance_schema.t_baseline.lock_user1_id IS '基线锁定人1（Part 11 §11.200 双签）';
COMMENT ON COLUMN compliance_schema.t_baseline.lock_signature_id1 IS '锁定人1 关联的电子签名 ID';
COMMENT ON COLUMN compliance_schema.t_baseline.lock_user2_id IS '基线锁定人2（必须 ≠ lockUser1Id）';
COMMENT ON COLUMN compliance_schema.t_baseline.lock_signature_id2 IS '锁定人2 关联的电子签名 ID';
