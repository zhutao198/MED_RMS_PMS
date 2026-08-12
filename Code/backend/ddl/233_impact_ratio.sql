-- Migration: 233_impact_ratio.sql
-- Date: 2026-08-12
-- Author: 朱涛
-- Reason: 补全变更影响评估报告（PRD 7.4.2 / FR-1.3）。
--   1) t_impact_assessment 增加 impact_ratio 列（影响范围百分比 = 受影响关联项/总关联项×100%）
--   2) 支持逐条明细（下游需求 / 关联测试用例 / 法规维度标记）
-- Related: ChangeService.performImpactAssessment

ALTER TABLE chg_schema.t_impact_assessment
    ADD COLUMN IF NOT EXISTS impact_ratio NUMERIC(5, 2);
