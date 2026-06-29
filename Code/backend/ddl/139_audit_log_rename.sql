-- DDL 139: 审计日志字段命名修正（v1.46 P0-后端-5）
-- 2026-06-08 zhutao
--
-- 背景：DDL 125 (v1.45 P0 修复) 将 old_value/new_value 重命名为 before_value/after_value
-- 以匹配当时实体字段。但这与详细设计 compliance-详细设计.md §1 类图 (AuditLog 实体)
-- 不一致 —— 设计要求 oldValueJson/newValueJson (Java) ↔ old_value/new_value (SQL)。
--
-- 偏差影响：
--   1. ETL 脚本若按设计名引用（如 SELECT old_value FROM t_audit_log）会找不到列
--   2. API 消费者按设计名（oldValueJson）解析 JSON 失败
--
-- 修复策略：回退 DDL 125 的重命名，DB 与 Java 字段名都按设计规范对齐
--   before_value  → old_value
--   after_value   → new_value
-- 同时把 beforeValue/afterValue 实体字段重命名为 oldValue/newValue

-- 1. 字段重命名
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'compliance_schema'
                 AND table_name = 't_audit_log'
                 AND column_name = 'before_value') THEN
        ALTER TABLE compliance_schema.t_audit_log RENAME COLUMN before_value TO old_value;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'compliance_schema'
                 AND table_name = 't_audit_log'
                 AND column_name = 'after_value') THEN
        ALTER TABLE compliance_schema.t_audit_log RENAME COLUMN after_value TO new_value;
    END IF;
END $$;

-- 2. 同步索引（如果有按 before_value/after_value 的索引，重命名以保持一致）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_indexes
               WHERE schemaname = 'compliance_schema'
                 AND tablename = 't_audit_log'
                 AND indexname = 'idx_audit_log_before_value') THEN
        ALTER INDEX compliance_schema.idx_audit_log_before_value RENAME TO idx_audit_log_old_value;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_indexes
               WHERE schemaname = 'compliance_schema'
                 AND tablename = 't_audit_log'
                 AND indexname = 'idx_audit_log_after_value') THEN
        ALTER INDEX compliance_schema.idx_audit_log_after_value RENAME TO idx_audit_log_new_value;
    END IF;
END $$;

-- 3. 注释更新
COMMENT ON COLUMN compliance_schema.t_audit_log.old_value IS '变更前值 JSON 字符串（21 CFR Part 11 §11.10(e) 必填）';
COMMENT ON COLUMN compliance_schema.t_audit_log.new_value IS '变更后值 JSON 字符串（21 CFR Part 11 §11.10(e) 必填）';

-- 4. 数据完整性确认：旧列名已无残留
SELECT
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = 'compliance_schema' AND table_name = 't_audit_log'
       AND column_name IN ('before_value', 'after_value')) AS old_columns_remaining,
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = 'compliance_schema' AND table_name = 't_audit_log'
       AND column_name IN ('old_value', 'new_value')) AS new_columns_present;

SELECT 'DDL 139: audit log renamed before_value/after_value → old_value/new_value' AS status;
