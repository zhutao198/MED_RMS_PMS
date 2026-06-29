-- =========================================================================
-- 128_requirement_p0_fields.sql
-- v1.47 BUG #125/#130/#132 P0 修复：需求域字段补齐
--   1) Review 加多轮字段：reviewer_name, round, is_latest, final_decision, auto_submitted
--   2) RequirementVersion version_no INTEGER -> VARCHAR(32) 支持语义化版本号
--   3) Requirement.requirement_no 加 unique constraint（BUG #130 防并发重复）
-- =========================================================================

-- 1) Review 表补字段
ALTER TABLE req_schema.t_review ADD COLUMN IF NOT EXISTS reviewer_name    VARCHAR(64);
ALTER TABLE req_schema.t_review ADD COLUMN IF NOT EXISTS round            INTEGER DEFAULT 1;
ALTER TABLE req_schema.t_review ADD COLUMN IF NOT EXISTS is_latest        BOOLEAN DEFAULT TRUE;
ALTER TABLE req_schema.t_review ADD COLUMN IF NOT EXISTS final_decision   VARCHAR(32);
ALTER TABLE req_schema.t_review ADD COLUMN IF NOT EXISTS auto_submitted   BOOLEAN DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_review_req_round ON req_schema.t_review(requirement_id, round);
CREATE INDEX IF NOT EXISTS idx_review_latest     ON req_schema.t_review(requirement_id, is_latest);

-- 2) RequirementVersion version_no 改 VARCHAR 支持 v1.0 / v1.1
ALTER TABLE req_schema.t_requirement_version ALTER COLUMN version_no TYPE VARCHAR(32);

-- 3) requirement_no unique constraint
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_requirement_no'
    ) THEN
        ALTER TABLE req_schema.t_requirement
            ADD CONSTRAINT uk_requirement_no UNIQUE (requirement_no);
    END IF;
END $$;

-- 4) BUG #131：CTI 子表加独立 id 主键 + requirementId 外键
-- 若表无 id 列则添加（MyBatis Plus 自增）
DO $$
DECLARE
    tbl TEXT;
    cols TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['t_user_requirement','t_product_requirement','t_system_requirement','t_design_requirement']
    LOOP
        -- 1. 加 id 列（若不存在）
        EXECUTE format('ALTER TABLE req_schema.%I ADD COLUMN IF NOT EXISTS id BIGSERIAL PRIMARY KEY', tbl);
        -- 2. 加 requirement_id 列（若不存在）
        EXECUTE format('ALTER TABLE req_schema.%I ADD COLUMN IF NOT EXISTS requirement_id BIGINT', tbl);
        -- 3. 把现有 req_id 数据复制到 requirement_id
        EXECUTE format('UPDATE req_schema.%I SET requirement_id = req_id WHERE requirement_id IS NULL AND req_id IS NOT NULL', tbl);
        -- 4. 加外键
        EXECUTE format('ALTER TABLE req_schema.%I DROP CONSTRAINT IF EXISTS fk_%I_requirement', tbl, tbl);
        EXECUTE format('ALTER TABLE req_schema.%I ADD CONSTRAINT fk_%I_requirement FOREIGN KEY (requirement_id) REFERENCES req_schema.t_requirement(id) ON DELETE CASCADE', tbl, tbl);
        -- 5. 索引
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%I_requirement_id ON req_schema.%I(requirement_id)', tbl, tbl);
    END LOOP;
END $$;

COMMENT ON TABLE req_schema.t_review IS '需求评审记录（v1.47 BUG #132 多轮评审）';
COMMENT ON TABLE req_schema.t_requirement_version IS '需求版本历史（v1.47 BUG #127 语义化版本号）';
