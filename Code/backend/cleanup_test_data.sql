-- Med-RMS 清理测试残留数据（W15 e2e 修复）
-- 删除 88 个测试需求 + 16 个 smoke 项目
-- 执行：psql -h localhost -U postgres -d med_rms_pms -f cleanup_test_data.sql
BEGIN;

-- 1) 删除测试需求（INTEG-TEST / AUDIT-AOP 前缀）
WITH deleted_reqs AS (
    DELETE FROM req_schema.t_requirement
    WHERE title LIKE 'INTEG-TEST-%' OR title LIKE 'AUDIT-AOP-%'
    RETURNING id
)
SELECT 'deleted_requirements' AS operation, COUNT(*) AS n FROM deleted_reqs;

-- 2) 删除测试项目（smoke 名称）
WITH deleted_projs AS (
    DELETE FROM proj_schema.t_project
    WHERE project_name = 'smoke'
    RETURNING id
)
SELECT 'deleted_projects' AS operation, COUNT(*) AS n FROM deleted_projs;

-- 3) 验证清理结果
SELECT 'remaining_requirements' AS check_name, COUNT(*) AS n FROM req_schema.t_requirement
UNION ALL
SELECT 'remaining_projects', COUNT(*) FROM proj_schema.t_project
UNION ALL
SELECT 'remaining_INTEG-TEST', COUNT(*) FROM req_schema.t_requirement WHERE title LIKE 'INTEG-TEST-%'
UNION ALL
SELECT 'remaining_AUDIT-AOP', COUNT(*) FROM req_schema.t_requirement WHERE title LIKE 'AUDIT-AOP-%'
UNION ALL
SELECT 'remaining_smoke', COUNT(*) FROM proj_schema.t_project WHERE project_name = 'smoke';

COMMIT;
