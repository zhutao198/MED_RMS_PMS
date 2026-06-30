-- ============================================================================
-- DDL 148: 字符编码迁移方案（PostgreSQL SQL_ASCII → UTF-8）— R119 中期方案
-- 2026-06-30 QClaw
--
-- ⚠️ 这是迁移方案文档，不是自动执行脚本！
-- 实际执行需 DBA 配合，按以下步骤手工操作。
--
-- 背景：
--   1. PostgreSQL 数据库初始化时使用 SQL_ASCII 编码（init_database.sql）
--   2. SQL_ASCII 实际上不做字符集校验，按字节存储 → 中文乱码（通知/问题报告）
--   3. 应用层 URL 已加 ?useUnicode=true&characterEncoding=utf-8
--   4. 需要在 DB 层做编码迁移以彻底解决中文乱码
--
-- 风险评估：
--   - 中等风险：DDL 涉及全库重建，需要停机
--   - 不可逆：一旦转 UTF-8，无法回退 SQL_ASCII
--   - 数据丢失风险：若有非 UTF-8 编码的中文数据，转码后可能丢失
--
-- ============================================================================

-- ============ Step 1: 备份（必须）============
-- pg_dump 全库备份
-- pg_dump -h localhost -U postgres -d med_rms_pms -F c -f med_rms_pms_backup_$(date +%Y%m%d).dump

-- ============ Step 2: 检查当前编码 ============
-- SELECT datname, datcollate, datctype, datlocprovider, datistemplate
-- FROM pg_database WHERE datname = 'med_rms_pms';

-- ============ Step 3: 迁移流程（pg_upgrade 或 pg_dump/restore）============
-- 方案 A：pg_dump/restore（推荐，数据量 < 10GB）
--   1. pg_dump -h localhost -U postgres -d med_rms_pms -F c -f backup.dump
--   2. dropdb med_rms_pms
--   3. createdb -E UTF8 -l zh_CN.UTF-8 -T template0 med_rms_pms
--   4. pg_restore -h localhost -U postgres -d med_rms_pms backup.dump
--   5. 验证：\l med_rms_pms → Encoding = UTF8

-- 方案 B：ALTER DATABASE（部分支持，需要全部数据兼容）
--   ALTER DATABASE med_rms_pms SET encoding TO 'UTF8';  -- ❌ PostgreSQL 不支持
--   （必须用方案 A）

-- ============ Step 4: 验证 Schema ============
-- SELECT n.nspname, c.relname, c.relpages
-- FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
-- WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
-- ORDER BY n.nspname, c.relname;

-- ============ Step 5: 验证字符集 ============
-- SELECT column_name, data_type, character_maximum_length
-- FROM information_schema.columns
-- WHERE table_schema = 'public' AND data_type IN ('varchar', 'text', 'char');

-- ============ Step 6: 测试中文写入 ============
-- INSERT INTO sys_schema.t_dict (code, dict_type, dict_name, status)
-- VALUES ('TEST_UTF8', 'TEST', '中文测试 UTF-8', 'ACTIVE');

-- SELECT dict_name FROM sys_schema.t_dict WHERE code = 'TEST_UTF8';
-- 期望结果："中文测试 UTF-8"（非乱码）

-- ============ 预计停机时间 ============
-- 数据量 < 10GB：30-60 分钟
-- 数据量 10-100GB：2-4 小时
-- 数据量 > 100GB：8+ 小时

COMMENT ON DATABASE med_rms_pms IS '医疗器械需求管理系统 | R119 待迁移: SQL_ASCII → UTF-8';

-- ============================================================================
-- 应用层验证（迁移后必跑）
-- ============================================================================
-- 1. 登录：admin / admin123
-- 2. 创建中文标题需求：POST /api/requirements {"title": "测试中文 UTF-8"}
-- 3. 查看：GET /api/requirements/{id} → title 字段应显示"测试中文 UTF-8"
-- 4. 数据库验证：SELECT title FROM req_schema.t_requirement WHERE id = {id};
-- ============================================================================