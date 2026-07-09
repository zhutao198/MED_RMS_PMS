-- R162: DB 层触发器防篡改（21 CFR Part 11 11.10(e) / 11.50）
-- 防止审计日志和电子签名记录被直接修改或删除

-- 1. t_audit_log 防篡改触发器
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

DROP TRIGGER IF EXISTS trg_audit_log_immutable ON compliance_schema.t_audit_log;
CREATE TRIGGER trg_audit_log_immutable
  BEFORE UPDATE OR DELETE ON compliance_schema.t_audit_log
  FOR EACH ROW EXECUTE FUNCTION compliance_schema.fn_prevent_audit_log_mutation();

-- 2. t_signature_record 防篡改触发器
CREATE OR REPLACE FUNCTION esign_schema.fn_prevent_signature_mutation()
RETURNS TRIGGER AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION '电子签名记录不可删除 (21 CFR Part 11 11.50)';
  END IF;
  IF TG_OP = 'UPDATE' THEN
    IF OLD.is_valid IS DISTINCT FROM NEW.is_valid THEN
      RETURN NEW;
    END IF;
    RAISE EXCEPTION '电子签名记录不可修改 (21 CFR Part 11 11.50)';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_signature_immutable ON esign_schema.t_signature_record;
CREATE TRIGGER trg_signature_immutable
  BEFORE UPDATE OR DELETE ON esign_schema.t_signature_record
  FOR EACH ROW EXECUTE FUNCTION esign_schema.fn_prevent_signature_mutation();
