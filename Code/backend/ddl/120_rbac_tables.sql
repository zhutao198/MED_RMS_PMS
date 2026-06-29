-- ============================================================================
-- 120. RBAC 权限矩阵表（v1.24 R25）
-- 关联 t_role / t_user / t_permission 三方
-- 设计依据：Detailed/04-权限设计/权限流程设计.md
-- 8 角色 + 62 权限码 + M:N 关联
-- ============================================================================

-- 权限点表
CREATE TABLE IF NOT EXISTS sys_schema.t_permission (
    id BIGSERIAL PRIMARY KEY,
    perm_code VARCHAR(100) NOT NULL UNIQUE,
    perm_name VARCHAR(100) NOT NULL,
    perm_type VARCHAR(20) NOT NULL DEFAULT 'API',  -- MENU/BUTTON/API
    resource_path VARCHAR(200),
    description VARCHAR(200),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 角色-权限关联表
CREATE TABLE IF NOT EXISTS sys_schema.t_role_permission (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL REFERENCES sys_schema.t_role(id) ON DELETE CASCADE,
    perm_id BIGINT NOT NULL REFERENCES sys_schema.t_permission(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (role_id, perm_id)
);

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS sys_schema.t_user_role (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_schema.t_user(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES sys_schema.t_role(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_perm_code ON sys_schema.t_permission(perm_code);
CREATE INDEX IF NOT EXISTS idx_role_perm_role ON sys_schema.t_role_permission(role_id);
CREATE INDEX IF NOT EXISTS idx_role_perm_perm ON sys_schema.t_role_permission(perm_id);
CREATE INDEX IF NOT EXISTS idx_user_role_user ON sys_schema.t_user_role(user_id);
CREATE INDEX IF NOT EXISTS idx_user_role_role ON sys_schema.t_user_role(role_id);
