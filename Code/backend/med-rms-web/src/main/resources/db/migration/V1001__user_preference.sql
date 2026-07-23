-- R215 v1.71: User Preference 表（Dashboard 持久化 + 跨设备同步）
--
-- 设计：key-value JSON 存储，每个用户每个 key 一条记录
-- 典型 keys:
--   dashboard.layout         - 仪表盘 widget 显示/隐藏配置
--   dashboard.refreshInterval - 自动刷新间隔（秒）
--   dashboard.perspective    - 视角偏好（R212）
--   requirement.filter.urs   - 需求筛选偏好（按角色/类型）

CREATE TABLE IF NOT EXISTS sys_schema.t_user_preference (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    pref_key VARCHAR(100) NOT NULL,
    pref_value JSONB,
    updated_at TIMESTAMP DEFAULT NOW(),
    created_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uk_user_pref_key UNIQUE (user_id, pref_key)
);

CREATE INDEX IF NOT EXISTS idx_user_pref_user_id ON sys_schema.t_user_preference(user_id);

COMMENT ON TABLE sys_schema.t_user_preference IS 'R215 用户偏好表（key-value JSON）';
COMMENT ON COLUMN sys_schema.t_user_preference.user_id IS '用户 ID';
COMMENT ON COLUMN sys_schema.t_user_preference.pref_key IS '偏好键（dashboard.layout / dashboard.refreshInterval 等）';
COMMENT ON COLUMN sys_schema.t_user_preference.pref_value IS '偏好值（JSONB 灵活结构）';
