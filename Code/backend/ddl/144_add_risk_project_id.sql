-- Med-RMS DDL v1.x R109 修复
-- 风险登记表 t_risk_register 新增 project_id 列（实现 RisksMatrix 按项目过滤）
-- 现有数据 backfill 见 ddl/145_backfill_risk_project_id.sql

ALTER TABLE risk_schema.t_risk_register
    ADD COLUMN IF NOT EXISTS project_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_risk_register_project_id
    ON risk_schema.t_risk_register(project_id)
    WHERE is_deleted = FALSE;