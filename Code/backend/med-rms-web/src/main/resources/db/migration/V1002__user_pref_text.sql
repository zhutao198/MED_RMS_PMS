-- R215.1: 修复 V1001 JSONB 类型（应用层传 String 不被 PostgreSQL JSONB 接受）
-- 改为 TEXT，应用层负责 JSON 序列化/反序列化
ALTER TABLE sys_schema.t_user_preference
    ALTER COLUMN pref_value TYPE TEXT USING pref_value::TEXT;
