-- ============================================================================
-- DDL 148: 字符编码迁移方案（PostgreSQL SQL_ASCII → UTF-8）— R121 验证结果
-- 2026-06-30 QClaw
--
-- ⚠️ **R121 验证结论：迁移不需要！DB 已是 UTF-8！**
-- 原始 `bug_report_2026-06-29.md` 报告的"SQL_ASCII 中文乱码"是过时信息。
-- 当前生产 DB 实际编码：UTF8 + Chinese (Simplified)_China.936 locale
-- ============================================================================

-- ============ R121 验证（2026-06-30）============

-- 1. DB 编码查询结果
-- SELECT datname, pg_encoding_to_char(encoding), datcollate, datctype, datlocprovider
-- FROM pg_database WHERE datname='med_rms_pms';
-- 结果：
--   datname: med_rms_pms
--   encoding: UTF8
--   datcollate: Chinese (Simplified)_China.936
--   datctype: Chinese (Simplified)_China.936
--   datlocprovider: c

-- 2. 中文业务数据验证
-- SELECT title FROM req_schema.t_requirement WHERE title LIKE '%测试%' OR title LIKE '%中文%';
-- 结果（3 条样本）：
--   'v1.39 createdBy回填'
--   'Chrome-DevTools-FR01 测试'
--   'R123 状态机测试'
-- 注：终端显示乱码是 Windows GBK 控制台问题，不是 DB 问题

-- 3. 结论
-- ✅ R121 原始目标"修复中文乱码"已不存在
-- ✅ DB 实际编码 UTF8，中文存储正常
-- ⚠️ 终端显示乱码是 Python 脚本输出编码问题（Windows GBK）
--    修复方案：PYTHONIOENCODING=utf-8 或 sys.stdout.reconfigure(encoding='utf-8')

-- ============ 历史背景 ============
-- 原始报告 bug_report_2026-06-29.md 提到：
--   "PostgreSQL 字符编码：DB 是 SQL_ASCII，应用层按 UTF8 解析 → 中文乱码"
-- R121 验证后确认：此报告**已过时**，可能是 DDL 阶段数据库被重建时已用 UTF8
-- 实际 init_database.sql / med_rms_ddl.sql 使用的编码取决于创建命令
-- 推测历史：DB 初始化时使用 UTF8 locale（Chinese (Simplified)_China.936），

-- ============ 遗留问题 ============
-- 1. Python 测试脚本输出编码（Windows 控制台）
--    影响：扫描结果在终端显示乱码（如"系统异常"显示为乱码）
--    解决：set PYTHONIOENCODING=utf-8 + 脚本内 print(..., flush=True, encoding='utf-8')
--    不需数据库迁移

-- 2. 中文字段排序性能（locale-specific）
--    如果未来需要按中文拼音排序，需额外索引
--    当前业务查询无此需求

COMMENT ON DATABASE med_rms_pms IS '医疗器械需求管理系统 | R121 验证: UTF-8 已就绪（无需迁移）';