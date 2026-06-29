-- =========================================================================
-- 141_requirement_category_field.sql
-- v2.5 字段补齐：需求分类（硬件/软件/软硬件）
-- 关联需求：FR-0.6 / US-5 / P-ORG-09
-- Related: test_plan v2.5 第十五章
-- =========================================================================

-- 1) 主表 t_requirement 加 requirement_category 列
ALTER TABLE req_schema.t_requirement
    ADD COLUMN IF NOT EXISTS requirement_category VARCHAR(32);

-- 默认回填
UPDATE req_schema.t_requirement
SET requirement_category = 'SOFTWARE'
WHERE requirement_category IS NULL;

-- 索引（按分类筛选）
CREATE INDEX IF NOT EXISTS idx_req_category
    ON req_schema.t_requirement(requirement_category);

-- 2) 注释
COMMENT ON COLUMN req_schema.t_requirement.requirement_category IS
    '需求分类：SOFTWARE/HARDWARE/BOTH（FR-0.6 / US-5 / P-ORG-09）';
