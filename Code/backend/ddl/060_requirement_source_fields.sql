-- Migration: 060_requirement_source_fields.sql
-- Date: 2026-06-02
-- Author: 朱涛
-- Reason: 补齐 FR-0.6 需求来源/来源编号字段（PRD US-5 验收）
-- 关联需求：FR-0.6, US-5
-- Related: v1.19 / R19 / 变更2

-- 说明：
-- 1. 原始 init_database.sql 中 `source Requirement,` 是 SQL 笔误（"Requirement" 不是合法类型），
--    导致该列从未被创建，需求来源信息无法记录。
-- 2. 本次迁移显式添加 source、source_no 两列，与 PRD US-5 字段定义对齐。
-- 3. 现有需求记录的 source 默认置为 'INTERNAL'（历史回填）。

ALTER TABLE req_schema.t_requirement
    ADD COLUMN IF NOT EXISTS source VARCHAR(20);

ALTER TABLE req_schema.t_requirement
    ADD COLUMN IF NOT EXISTS source_no VARCHAR(100);

-- 历史数据回填：缺失 source 字段的需求默认标记为 'INTERNAL'
UPDATE req_schema.t_requirement
SET source = 'INTERNAL'
WHERE source IS NULL;

-- 索引：方便按来源筛选
CREATE INDEX IF NOT EXISTS idx_req_source ON req_schema.t_requirement(source);
