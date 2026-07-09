-- =========================================================================
-- r162_field_constraints.sql
-- FR-0.6 DB 字段 NOT NULL + CHECK 约束
-- 关联需求：FR-0.6（四层需求字段定义与校验）
-- =========================================================================

-- 1) 回填现有 NULL 数据（NOT NULL 前置条件）
UPDATE req_schema.t_requirement SET requirement_no = 'MIGRATED-' || id WHERE requirement_no IS NULL;
UPDATE req_schema.t_requirement SET requirement_type = 'URS' WHERE requirement_type IS NULL;
UPDATE req_schema.t_requirement SET project_id = 1 WHERE project_id IS NULL;
UPDATE req_schema.t_requirement SET title = '（已迁移）' WHERE title IS NULL;
UPDATE req_schema.t_requirement SET description = '' WHERE description IS NULL;
UPDATE req_schema.t_requirement SET priority = 'SHOULD' WHERE priority IS NULL;
UPDATE req_schema.t_requirement SET status = 'Draft' WHERE status IS NULL;
UPDATE req_schema.t_requirement SET risk_level = 'MEDIUM' WHERE risk_level IS NULL;
UPDATE req_schema.t_requirement SET safety_class = 'B' WHERE safety_class IS NULL;

-- 2) NOT NULL 约束
ALTER TABLE req_schema.t_requirement ALTER COLUMN requirement_no SET NOT NULL;
ALTER TABLE req_schema.t_requirement ALTER COLUMN requirement_type SET NOT NULL;
ALTER TABLE req_schema.t_requirement ALTER COLUMN project_id SET NOT NULL;
ALTER TABLE req_schema.t_requirement ALTER COLUMN title SET NOT NULL;
ALTER TABLE req_schema.t_requirement ALTER COLUMN description SET NOT NULL;
ALTER TABLE req_schema.t_requirement ALTER COLUMN priority SET NOT NULL;
ALTER TABLE req_schema.t_requirement ALTER COLUMN status SET NOT NULL;
ALTER TABLE req_schema.t_requirement ALTER COLUMN risk_level SET NOT NULL;
ALTER TABLE req_schema.t_requirement ALTER COLUMN safety_class SET NOT NULL;

-- 3) CHECK 约束（枚举值白名单）
ALTER TABLE req_schema.t_requirement ADD CONSTRAINT ck_requirement_type
    CHECK (requirement_type IN ('URS', 'PRS', 'SRS', 'DRS'));
ALTER TABLE req_schema.t_requirement ADD CONSTRAINT ck_priority
    CHECK (priority IN ('MUST', 'SHOULD', 'COULD', 'WONT'));
ALTER TABLE req_schema.t_requirement ADD CONSTRAINT ck_risk_level
    CHECK (risk_level IN ('HIGH', 'MEDIUM', 'LOW'));
ALTER TABLE req_schema.t_requirement ADD CONSTRAINT ck_safety_class
    CHECK (safety_class IN ('A', 'B', 'C'));
ALTER TABLE req_schema.t_requirement ADD CONSTRAINT ck_requirement_category
    CHECK (requirement_category IS NULL OR requirement_category IN ('SOFTWARE', 'HARDWARE', 'BOTH'));
ALTER TABLE req_schema.t_requirement ADD CONSTRAINT ck_source
    CHECK (source IS NULL OR source IN (
        'CUSTOMER', 'MARKET', 'REGULATION', 'INTERNAL', 'COMPETITOR',
        'USER_INTERVIEW', 'HISTORICAL_PROJECT', 'CUSTOMER_COMPLAINT',
        'EXPERT_REVIEW', 'SYSTEM_LOG', 'OTHER'
    ));

COMMENT ON CONSTRAINT ck_requirement_type ON req_schema.t_requirement IS 'FR-0.6: 需求层级白名单';
COMMENT ON CONSTRAINT ck_priority ON req_schema.t_requirement IS 'FR-0.6: 优先级白名单';
COMMENT ON CONSTRAINT ck_risk_level ON req_schema.t_requirement IS 'FR-0.6: 风险等级白名单';
COMMENT ON CONSTRAINT ck_safety_class ON req_schema.t_requirement IS 'FR-0.6: 安全分类白名单';
COMMENT ON CONSTRAINT ck_requirement_category ON req_schema.t_requirement IS 'FR-0.6: 需求分类白名单';
COMMENT ON CONSTRAINT ck_source ON req_schema.t_requirement IS 'FR-0.6: 需求来源白名单';
