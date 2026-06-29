-- Migration: 070_suspect_flag.sql
-- Date: 2026-06-02
-- Author: 朱涛
-- Reason: FR-0.10 变更影响自动标记（suspect）
-- Related: v1.19 / R19 / 变更4

ALTER TABLE req_schema.t_requirement
    ADD COLUMN IF NOT EXISTS is_suspect BOOLEAN DEFAULT FALSE;

ALTER TABLE req_schema.t_test_case
    ADD COLUMN IF NOT EXISTS is_suspect BOOLEAN DEFAULT FALSE;

-- 索引：方便按 suspect 状态筛选
CREATE INDEX IF NOT EXISTS idx_req_suspect ON req_schema.t_requirement(is_suspect) WHERE is_suspect = TRUE;
CREATE INDEX IF NOT EXISTS idx_tc_suspect ON req_schema.t_test_case(is_suspect) WHERE is_suspect = TRUE;
