-- =============================================================================
-- R162: sys:* 通配权限（系统管理模块全权限）
-- 为 QA_MGR / COMPLIANCE / RISK_MGR 批量授予系统管理模块全部权限
-- ADMIN 已有 `*`，不再重复授予
-- =============================================================================

BEGIN;

-- ===================================================
-- 1. 插入 sys:* 通配权限（幂等：perm_code 唯一约束）
-- ===================================================
INSERT INTO sys_schema.t_permission (perm_code, perm_name, perm_type, status)
VALUES ('sys:*', '系统管理通配', 'API', 'ACTIVE')
ON CONFLICT (perm_code) DO NOTHING;

-- ===================================================
-- 2. 授予 QA_MGR / COMPLIANCE / RISK_MGR
--    通过 role_code 子查询获取 role_id
-- ===================================================
INSERT INTO sys_schema.t_role_permission (role_id, perm_id)
SELECT r.id, p.id
FROM sys_schema.t_role r
CROSS JOIN sys_schema.t_permission p
WHERE r.role_code IN ('QA_MGR', 'COMPLIANCE', 'RISK_MGR')
  AND p.perm_code = 'sys:*'
ON CONFLICT (role_id, perm_id) DO NOTHING;

COMMIT;

-- ===================================================
-- 验证
-- ===================================================
SELECT 'R162 验证' AS check_name,
       r.role_code,
       p.perm_code
FROM sys_schema.t_role_permission rp
JOIN sys_schema.t_role r ON rp.role_id = r.id
JOIN sys_schema.t_permission p ON rp.perm_id = p.id
WHERE p.perm_code = 'sys:*'
ORDER BY r.role_code;
