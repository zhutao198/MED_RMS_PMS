-- ============================================================
-- 151: 需求收集池 id 改为 VARCHAR(20)（日期时间自动编号格式）
-- 迁移后 id 示例：20260713001（2026-07-13 第 1 条）
-- ============================================================

-- 1. 删除自增默认值
ALTER TABLE req_schema.t_requirement_pool ALTER COLUMN id DROP DEFAULT;

-- 2. 删除关联序列
DROP SEQUENCE IF EXISTS req_schema.t_requirement_pool_id_seq;

-- 3. 列类型改为 VARCHAR(20)，已有 bigint 值转文本
ALTER TABLE req_schema.t_requirement_pool ALTER COLUMN id TYPE VARCHAR(20) USING id::text;
