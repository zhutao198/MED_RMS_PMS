-- R214 v1.70: Flyway 数据库迁移初始化（FR-1.13 数据迁移工具链）
--
-- 说明：
-- 1. 这是 Flyway 启用后的第一个迁移（baseline 之后的第一个版本）
-- 2. 现有 DDL（ddl/030-*.sql ~ r207.sql）已手工应用，本文件不重复执行
-- 3. 后续新表/字段/索引变更请在本目录下创建 V1001+__description.sql
-- 4. 命名规范：V{版本}__{简述}.sql（如 V1001__add_safety_audit.sql）
-- 5. 不可变规则：已应用的迁移禁止修改！需要修改请创建 V{new_version}__fix_xxx.sql
--
-- 本文件用于：
--  a) 触发 Flyway 写入 flyway_schema_history 表（验证迁移链路）
--  b) 为后续迁移提供锚点

-- 示例：插入迁移元数据（可选，便于查询）
-- INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
-- VALUES (1, '1000', 'flyway init', 'SQL', 'V1000__flyway_init.sql', 0, 'flyway', NOW(), 0, true)
-- ON CONFLICT DO NOTHING;

-- 占位：当前无实际 DDL 变更（保留 V1000 作为 baseline 之后的版本锚点）
SELECT 'Flyway V1000 initialized - Med-RMS v1.70' AS init_msg;
