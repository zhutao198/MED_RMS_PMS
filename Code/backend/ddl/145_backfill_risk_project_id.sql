-- Med-RMS DDL v1.x R109 backfill
-- 现有 3 条 RISK 都属于心电监护仪 v3.0 项目（id=1）
-- 后续新风险由前端在创建时传 projectId（待后续迭代实施）

UPDATE risk_schema.t_risk_register
SET project_id = 1
WHERE project_id IS NULL;