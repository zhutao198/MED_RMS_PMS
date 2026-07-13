-- R184: 需求收集池新增字段
-- 1. business_scenario 业务场景
-- 2. competitive_analysis 竞品分析
-- 3. rejection_reason 拒绝理由

ALTER TABLE req_schema.t_requirement_pool
  ADD COLUMN IF NOT EXISTS business_scenario TEXT,
  ADD COLUMN IF NOT EXISTS competitive_analysis TEXT,
  ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500);
