-- R164: 审计日志按月分区表（21 CFR Part 11 §11.10(e) 15年保留要求）
-- PostgreSQL 16 声明式 RANGE 分区（按 created_at 月粒度）
-- 列名与 DDL 125/139 对齐（current_hash, prev_hash, event_type, reason）

-- ============================================================================
-- 1. 重命名旧表
-- ============================================================================
ALTER TABLE compliance_schema.t_audit_log RENAME TO t_audit_log_legacy;
ALTER INDEX compliance_schema.idx_audit_entity   RENAME TO idx_audit_entity_legacy;
ALTER INDEX compliance_schema.idx_audit_operator RENAME TO idx_audit_operator_legacy;
ALTER INDEX compliance_schema.idx_audit_time     RENAME TO idx_audit_time_legacy;

-- ============================================================================
-- 2. 创建分区主表（与旧表列完全一致）
-- ============================================================================
CREATE TABLE compliance_schema.t_audit_log (
    id              BIGSERIAL       NOT NULL,
    entity_type     VARCHAR(50)     NOT NULL,
    entity_id       BIGINT          NOT NULL,
    operation       VARCHAR(50)     NOT NULL,
    operator_id     BIGINT,
    operator_name   VARCHAR(100),
    old_value       TEXT,
    new_value       TEXT,
    current_hash    VARCHAR(100),
    prev_hash       VARCHAR(100),
    ip_address      VARCHAR(50),
    user_agent      VARCHAR(200),
    is_deleted      BOOLEAN         DEFAULT FALSE,
    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    event_type      VARCHAR(50),
    reason          TEXT,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE  compliance_schema.t_audit_log IS '审计日志（按月分区，21 CFR Part 11 §11.10(e) 合规）';
COMMENT ON COLUMN compliance_schema.t_audit_log.current_hash IS '本条记录 SHA-256 哈希值';
COMMENT ON COLUMN compliance_schema.t_audit_log.prev_hash    IS '前一条记录的 SHA-256 哈希值';
COMMENT ON COLUMN compliance_schema.t_audit_log.event_type   IS '事件类型: CREATE/MODIFY/DELETE/STATUS_CHANGE/SIGN/REVIEW/APPROVE/EXECUTE';
COMMENT ON COLUMN compliance_schema.t_audit_log.reason       IS '操作原因/备注';

-- ============================================================================
-- 3. 创建分区（历史 + 当前月起 12 个月）
-- ============================================================================

-- 历史分区：承接旧表所有数据
CREATE TABLE compliance_schema.t_audit_log_history PARTITION OF compliance_schema.t_audit_log
    FOR VALUES FROM (MINVALUE) TO ('2026-07-01');

-- 当前月起 12 个月
DO $$
DECLARE
    start_date  DATE := '2026-07-01';
    end_date    DATE := '2027-07-01';
    part_date   DATE;
    part_name   TEXT;
    date_from   TEXT;
    date_to     TEXT;
BEGIN
    part_date := start_date;
    WHILE part_date < end_date LOOP
        part_name := 't_audit_log_' || TO_CHAR(part_date, 'YYYY_MM');
        date_from := TO_CHAR(part_date, 'YYYY-MM-DD');
        date_to   := TO_CHAR(part_date + INTERVAL '1 month', 'YYYY-MM-DD');
        EXECUTE format(
            'CREATE TABLE compliance_schema.%I PARTITION OF compliance_schema.t_audit_log
             FOR VALUES FROM (%L) TO (%L)',
            part_name, date_from, date_to
        );
        RAISE NOTICE 'Created partition: % (% to %)', part_name, date_from, date_to;
        part_date := part_date + INTERVAL '1 month';
    END LOOP;
END $$;

-- ============================================================================
-- 4. 迁移旧数据
-- ============================================================================
INSERT INTO compliance_schema.t_audit_log (
    id, entity_type, entity_id, operation, operator_id, operator_name,
    old_value, new_value, current_hash, prev_hash,
    ip_address, user_agent, is_deleted, created_at, event_type, reason
)
SELECT
    id, entity_type, entity_id, operation, operator_id, operator_name,
    old_value, new_value, current_hash, prev_hash,
    ip_address, user_agent, is_deleted, created_at, event_type, reason
FROM compliance_schema.t_audit_log_legacy
ORDER BY id;

SELECT setval('compliance_schema.t_audit_log_id_seq', coalesce(max(id), 1))
FROM compliance_schema.t_audit_log;

-- ============================================================================
-- 5. 重建索引
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_audit_entity   ON compliance_schema.t_audit_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_operator ON compliance_schema.t_audit_log(operator_id);

-- ============================================================================
-- 6. 重建防篡改触发器
-- ============================================================================
CREATE OR REPLACE FUNCTION compliance_schema.fn_prevent_audit_log_mutation()
RETURNS TRIGGER AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION '审计日志不可删除 (21 CFR Part 11 11.10(e))';
  END IF;
  IF TG_OP = 'UPDATE' THEN
    IF OLD.is_deleted IS DISTINCT FROM NEW.is_deleted THEN
      RETURN NEW;
    END IF;
    RAISE EXCEPTION '审计日志不可修改 (21 CFR Part 11 11.10(e))';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_immutable
    BEFORE UPDATE OR DELETE ON compliance_schema.t_audit_log
    FOR EACH ROW EXECUTE FUNCTION compliance_schema.fn_prevent_audit_log_mutation();

-- ============================================================================
-- 7. 自动创建下月分区
-- ============================================================================
CREATE OR REPLACE FUNCTION compliance_schema.fn_create_next_audit_partition()
RETURNS void AS $$
DECLARE
    next_month  DATE;
    part_name   TEXT;
    date_from   TEXT;
    date_to     TEXT;
BEGIN
    next_month := date_trunc('month', current_timestamp + INTERVAL '1 month');
    part_name  := 't_audit_log_' || to_char(next_month, 'YYYY_MM');
    date_from  := to_char(next_month, 'YYYY-MM-DD');
    date_to    := to_char(next_month + INTERVAL '1 month', 'YYYY-MM-DD');

    IF NOT EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = part_name AND n.nspname = 'compliance_schema'
    ) THEN
        EXECUTE format(
            'CREATE TABLE compliance_schema.%I PARTITION OF compliance_schema.t_audit_log
             FOR VALUES FROM (%L) TO (%L)',
            part_name, date_from, date_to
        );
        RAISE NOTICE 'Auto-created partition: %', part_name;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- 8. 验证
-- ============================================================================
SELECT
    'r164' AS migration,
    (SELECT count(*) FROM compliance_schema.t_audit_log)           AS new_table_count,
    (SELECT count(*) FROM compliance_schema.t_audit_log_legacy)    AS legacy_count,
    (SELECT count(DISTINCT pgc.relname)
     FROM pg_class pgc
     JOIN pg_inherits ON pgc.oid = pg_inherits.inhrelid
     WHERE pg_inherits.inhparent = 'compliance_schema.t_audit_log'::regclass
    )                                                              AS partition_count;
