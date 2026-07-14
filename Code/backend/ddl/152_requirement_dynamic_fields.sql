-- =========================================================================
-- 152_requirement_dynamic_fields.sql
-- R189: t_requirement 加 dynamic_fields 列，存储前端层级特有字段 JSON
--       解决 URS 特有字段（scenario/useCase/userRole/expectedOutcome）编辑后丢失
-- =========================================================================

ALTER TABLE req_schema.t_requirement ADD COLUMN IF NOT EXISTS dynamic_fields VARCHAR(2000);

COMMENT ON COLUMN req_schema.t_requirement.dynamic_fields IS
  '层级特有字段 JSON（URS: scenario/useCase/userRole/expectedOutcome; PRS: designConstraint/implementationApproach/affectedComponents; SRS: interfaceSpec/performanceTarget/dataStructure; DRS: algorithm/versionControlCode/deploymentEnv）';
