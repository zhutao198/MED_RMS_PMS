-- 清理 W16 e2e 走查发现的残留：integ_test 用户 + smoke 风险
BEGIN;

-- 1) 删除 37 个集成测试遗留用户
WITH deleted_users AS (
    DELETE FROM sys_schema.t_user
    WHERE username LIKE 'integ_test_%'
    RETURNING id
)
SELECT 'deleted_integ_test_users' AS op, COUNT(*) AS n FROM deleted_users;

-- 2) 删除 43 个 smoke/CROSS/DBG 风险登记
WITH deleted_risks AS (
    DELETE FROM risk_schema.t_risk_register
    WHERE risk_title LIKE 'smoke%'
       OR risk_title LIKE 'DBG-%'
       OR risk_title LIKE 'CROSS-F%'
       OR risk_title LIKE 'v1.43-smoke'
    RETURNING id
)
SELECT 'deleted_smoke_risks' AS op, COUNT(*) AS n FROM deleted_risks;

-- 3) 验证
SELECT 'remaining_users' AS check_name, COUNT(*) AS n FROM sys_schema.t_user
UNION ALL
SELECT 'remaining_integ_test', COUNT(*) FROM sys_schema.t_user WHERE username LIKE 'integ_test_%'
UNION ALL
SELECT 'remaining_risks', COUNT(*) FROM risk_schema.t_risk_register
UNION ALL
SELECT 'remaining_smoke', COUNT(*) FROM risk_schema.t_risk_register WHERE risk_title LIKE 'smoke%' OR risk_title LIKE 'CROSS-F%';

COMMIT;
