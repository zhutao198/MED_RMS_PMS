-- R197: 21 CFR Part 11 合规审计 — G16/G17 核心表防篡改触发器 + 记录级校验和
-- G16: 审核记录表（t_user / t_change_request / t_signature_intent）加 is_deleted + DELETE 阻止
-- G17: 核心业务表加 record_hash 列 + 自动哈希触发器（防篡改检测）

-- ==========================================================
-- G16.1: sys_schema.t_user 软删除支持
-- ==========================================================
ALTER TABLE sys_schema.t_user ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE;

-- G16.2: chg_schema.t_change_request 软删除支持
ALTER TABLE chg_schema.t_change_request ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE;

-- G16.3: esign_schema.t_signature_intent 软删除支持
ALTER TABLE esign_schema.t_signature_intent ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE;

-- G16.4: sys_schema.t_role 软删除支持
ALTER TABLE sys_schema.t_role ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE;

-- G16.5: proj_schema.t_milestone 软删除支持
ALTER TABLE proj_schema.t_milestone ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE;

-- G16.6: proj_schema.t_task 软删除支持
ALTER TABLE proj_schema.t_task ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE;

-- G16.7: req_schema.t_requirement_pool 软删除支持
ALTER TABLE req_schema.t_requirement_pool ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE;

-- ==========================================================
-- G16.8: 通用 DELETE 阻止触发器（所有已存在 is_deleted 列的核心表）
-- 原理：表已有 is_deleted 列，业务层用 UPDATE is_deleted=true 代替 DELETE
--       此处 DB 层阻断硬删除
-- ==========================================================

CREATE OR REPLACE FUNCTION public.fn_prevent_hard_delete()
RETURNS TRIGGER AS $$
BEGIN
  RAISE EXCEPTION '禁止硬删除 (21 CFR Part 11 11.10(c)): 请使用软删除 (UPDATE is_deleted=true)'
    USING HINT = '表 ' || TG_TABLE_SCHEMA || '.' || TG_TABLE_NAME || ' 设置了 DELETE 触发器';
END;
$$ LANGUAGE plpgsql;

-- 应用 DELETE 阻止触发器到所有核心业务表
DO $$ 
DECLARE
  tbl RECORD;
BEGIN
  FOR tbl IN 
    SELECT table_schema, table_name 
    FROM information_schema.columns 
    WHERE column_name = 'is_deleted' 
      AND table_schema IN ('req_schema','chg_schema','proj_schema','risk_schema',
                           'compliance_schema','esign_schema','sys_schema')
      AND table_name NOT LIKE '%audit_log%'      -- 审计日志已有独立触发器
      AND table_name NOT LIKE '%backup%'
      AND table_name NOT LIKE '%legacy%'
      AND table_name NOT LIKE '%history%'
  LOOP
    EXECUTE format(
      'DROP TRIGGER IF EXISTS trg_prevent_hard_delete ON %I.%I; '
      'CREATE TRIGGER trg_prevent_hard_delete '
      '  BEFORE DELETE ON %I.%I '
      '  FOR EACH ROW EXECUTE FUNCTION public.fn_prevent_hard_delete();',
      tbl.table_schema, tbl.table_name,
      tbl.table_schema, tbl.table_name
    );
  END LOOP;
END $$;

-- ==========================================================
-- G17: 记录级 SHA-256 校验和（tamper detection）
-- 为每个 INSERT/UPDATE 自动计算 record_hash
-- verify 时可重算比对，检测数据是否被直接修改
-- ==========================================================

-- G17.1: 为核心表增加 record_hash 列
DO $$
BEGIN
  -- req_schema.t_requirement
  EXECUTE 'ALTER TABLE req_schema.t_requirement ADD COLUMN IF NOT EXISTS record_hash VARCHAR(64)';
  -- chg_schema.t_change_request
  EXECUTE 'ALTER TABLE chg_schema.t_change_request ADD COLUMN IF NOT EXISTS record_hash VARCHAR(64)';
  -- proj_schema.t_project
  EXECUTE 'ALTER TABLE proj_schema.t_project ADD COLUMN IF NOT EXISTS record_hash VARCHAR(64)';
  -- sys_schema.t_user
  EXECUTE 'ALTER TABLE sys_schema.t_user ADD COLUMN IF NOT EXISTS record_hash VARCHAR(64)';
  -- sys_schema.t_role
  EXECUTE 'ALTER TABLE sys_schema.t_role ADD COLUMN IF NOT EXISTS record_hash VARCHAR(64)';
  -- risk_schema.t_risk_assessment
  EXECUTE 'ALTER TABLE risk_schema.t_risk_assessment ADD COLUMN IF NOT EXISTS record_hash VARCHAR(64)';
  -- proj_schema.t_baseline
  EXECUTE 'ALTER TABLE compliance_schema.t_baseline ADD COLUMN IF NOT EXISTS record_hash VARCHAR(64)';
END $$;

-- G17.2: 通用 record_hash 计算函数（排除 record_hash / id / created_at 自增/自动字段）
CREATE OR REPLACE FUNCTION public.fn_compute_record_hash()
RETURNS TRIGGER AS $$
DECLARE
  row_jsonb JSONB;
  keys_to_remove TEXT[] := ARRAY['record_hash', 'id', 'created_at'];
  k TEXT;
BEGIN
  row_jsonb := to_jsonb(NEW);
  FOREACH k IN ARRAY keys_to_remove LOOP
    row_jsonb := row_jsonb - k;
  END LOOP;
  NEW.record_hash := encode(
    sha256(convert_to(row_jsonb::text, 'UTF8')),
    'hex'
  );
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- G17.3: 为各核心表应用 record_hash 触发器
DO $$
BEGIN
  -- req_schema.t_requirement
  DROP TRIGGER IF EXISTS trg_record_hash ON req_schema.t_requirement;
  CREATE TRIGGER trg_record_hash
    BEFORE INSERT OR UPDATE ON req_schema.t_requirement
    FOR EACH ROW EXECUTE FUNCTION public.fn_compute_record_hash();

  -- chg_schema.t_change_request
  DROP TRIGGER IF EXISTS trg_record_hash ON chg_schema.t_change_request;
  CREATE TRIGGER trg_record_hash
    BEFORE INSERT OR UPDATE ON chg_schema.t_change_request
    FOR EACH ROW EXECUTE FUNCTION public.fn_compute_record_hash();

  -- proj_schema.t_project
  DROP TRIGGER IF EXISTS trg_record_hash ON proj_schema.t_project;
  CREATE TRIGGER trg_record_hash
    BEFORE INSERT OR UPDATE ON proj_schema.t_project
    FOR EACH ROW EXECUTE FUNCTION public.fn_compute_record_hash();

  -- sys_schema.t_user
  DROP TRIGGER IF EXISTS trg_record_hash ON sys_schema.t_user;
  CREATE TRIGGER trg_record_hash
    BEFORE INSERT OR UPDATE ON sys_schema.t_user
    FOR EACH ROW EXECUTE FUNCTION public.fn_compute_record_hash();

  -- sys_schema.t_role
  DROP TRIGGER IF EXISTS trg_record_hash ON sys_schema.t_role;
  CREATE TRIGGER trg_record_hash
    BEFORE INSERT OR UPDATE ON sys_schema.t_role
    FOR EACH ROW EXECUTE FUNCTION public.fn_compute_record_hash();

  -- risk_schema.t_risk_assessment
  DROP TRIGGER IF EXISTS trg_record_hash ON risk_schema.t_risk_assessment;
  CREATE TRIGGER trg_record_hash
    BEFORE INSERT OR UPDATE ON risk_schema.t_risk_assessment
    FOR EACH ROW EXECUTE FUNCTION public.fn_compute_record_hash();

  -- compliance_schema.t_baseline
  DROP TRIGGER IF EXISTS trg_record_hash ON compliance_schema.t_baseline;
  CREATE TRIGGER trg_record_hash
    BEFORE INSERT OR UPDATE ON compliance_schema.t_baseline
    FOR EACH ROW EXECUTE FUNCTION public.fn_compute_record_hash();
END $$;
