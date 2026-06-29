-- =========================================================================
-- 125_audit_log_schema_sync.sql
-- 修复审计日志表 schema 与 Java 实体不匹配
--
-- 实体 AuditLog.java 字段（MyBatis-Plus 驼峰→下划线）：
--   prevHash        → prev_hash
--   currentHash     → current_hash
--   eventType       → event_type
--   entityType      → entity_type
--   entityId        → entity_id
--   operatorId      → operator_id
--   operatorName    → operator_name
--   operation       → operation
--   beforeValue     → before_value
--   afterValue      → after_value
--   reason          → reason
--   ipAddress       → ip_address
--   createdAt       → created_at
--
-- 实际表 compliance_schema.t_audit_log 列：
--   id, entity_type, entity_id, operation, operator_id, operator_name,
--   old_value, new_value, hash_value, previous_hash, ip_address,
--   user_agent, is_deleted, created_at
--
-- 差异：实体有 current_hash/prev_hash/before_value/after_value/event_type/reason
--       表 有 hash_value/previous_hash/old_value/new_value/user_agent/is_deleted
--       表缺少 event_type / reason 列 → 必须 ALTER 添加
--       列重命名以保持语义一致
-- =========================================================================

-- 1. 添加缺失列
ALTER TABLE compliance_schema.t_audit_log
    ADD COLUMN IF NOT EXISTS event_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS reason TEXT;

-- 2. 重命名列以匹配实体（MyBatis-Plus 驼峰→snake）
DO $$
BEGIN
    -- hash_value → current_hash
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema='compliance_schema' AND table_name='t_audit_log'
                 AND column_name='hash_value') THEN
        ALTER TABLE compliance_schema.t_audit_log RENAME COLUMN hash_value TO current_hash;
    END IF;
    -- previous_hash → prev_hash
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema='compliance_schema' AND table_name='t_audit_log'
                 AND column_name='previous_hash') THEN
        ALTER TABLE compliance_schema.t_audit_log RENAME COLUMN previous_hash TO prev_hash;
    END IF;
    -- old_value → before_value
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema='compliance_schema' AND table_name='t_audit_log'
                 AND column_name='old_value') THEN
        ALTER TABLE compliance_schema.t_audit_log RENAME COLUMN old_value TO before_value;
    END IF;
    -- new_value → after_value
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema='compliance_schema' AND table_name='t_audit_log'
                 AND column_name='new_value') THEN
        ALTER TABLE compliance_schema.t_audit_log RENAME COLUMN new_value TO after_value;
    END IF;
END $$;

-- 3. 新增索引（event_type 用于按事件类型查询）
CREATE INDEX IF NOT EXISTS idx_audit_event_type ON compliance_schema.t_audit_log(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_entity_id  ON compliance_schema.t_audit_log(entity_id);

-- 4. 注释
COMMENT ON COLUMN compliance_schema.t_audit_log.prev_hash     IS '前一条记录的 SHA-256 哈希';
COMMENT ON COLUMN compliance_schema.t_audit_log.current_hash  IS '本条记录的 SHA-256 哈希';
COMMENT ON COLUMN compliance_schema.t_audit_log.event_type    IS '事件类型: CREATE/MODIFY/DELETE/STATUS_CHANGE/SIGN/REVIEW/APPROVE/EXECUTE';
COMMENT ON COLUMN compliance_schema.t_audit_log.before_value  IS '变更前值 JSON 字符串';
COMMENT ON COLUMN compliance_schema.t_audit_log.after_value   IS '变更后值 JSON 字符串';
COMMENT ON COLUMN compliance_schema.t_audit_log.reason        IS '操作原因/备注';

-- 5. 字段类型修正：before_value/after_value 由 jsonb 改为 TEXT（实体以 String 存 JSON 字符串）
ALTER TABLE compliance_schema.t_audit_log
    ALTER COLUMN before_value TYPE TEXT USING before_value::text,
    ALTER COLUMN after_value  TYPE TEXT USING after_value::text;
