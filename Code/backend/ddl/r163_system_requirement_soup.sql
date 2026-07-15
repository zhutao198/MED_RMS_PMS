-- R191: 修复 t_system_requirement 表缺少列，导致 gaps API 返回 SY0000
-- 根因：SystemRequirement 实体（module_name / api_spec / soup_component_id / test_case_ids）
-- 对应的数据库列缺失，findSoupUntraced() 查询时抛 SQL 异常

ALTER TABLE req_schema.t_system_requirement
  ADD COLUMN IF NOT EXISTS module_name       varchar(255),
  ADD COLUMN IF NOT EXISTS api_spec          text,
  ADD COLUMN IF NOT EXISTS soup_component_id bigint,
  ADD COLUMN IF NOT EXISTS test_case_ids     text;
