-- Migration: 232_risk_overtime_flags.sql
-- Date: 2026-08-12
-- Author: 朱涛
-- Reason: H1 方案 A — 拆分 suspect 语义过载。
--   is_suspect 仅保留给"变更影响/任务阻塞导致 Suspect"场景；
--   is_risk 用于差异 #4 阻塞超期（默认 3 天）；is_overtime 用于差异 #6 工时超支（> 预估 ×150%）。
-- Related: 需求流转流程梳理.md H1

ALTER TABLE req_schema.t_requirement
    ADD COLUMN IF NOT EXISTS is_risk BOOLEAN DEFAULT FALSE;

ALTER TABLE req_schema.t_requirement
    ADD COLUMN IF NOT EXISTS is_overtime BOOLEAN DEFAULT FALSE;

-- 索引：方便按风险/超支状态筛选
CREATE INDEX IF NOT EXISTS idx_req_risk ON req_schema.t_requirement(is_risk) WHERE is_risk = TRUE;
CREATE INDEX IF NOT EXISTS idx_req_overtime ON req_schema.t_requirement(is_overtime) WHERE is_overtime = TRUE;
