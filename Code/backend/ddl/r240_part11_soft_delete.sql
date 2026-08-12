-- R240 / V1004: 21 CFR Part 11 §11.10(c) 软删除合规迁移
-- 提交日期: 2026-08-10
-- 关联代码修复: P1-4 (CODE_REVIEW_FULL_VERIFICATION_2026-08-10.md)
--
-- 此文件是 Flyway V1004 的 ddl/ 审计副本。Flyway 启动时执行 V1004，
-- 本文件保留作为历史变更追溯。

-- 注: 实际可执行版本位于
-- med-rms-web/src/main/resources/db/migration/V1004__part11_soft_delete.sql

-- ==========================================================
-- 4 张核心表添加 is_deleted 列
-- ==========================================================
-- ALTER TABLE prj_schema.t_task_predecessor
--     ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
-- ALTER TABLE prj_schema.t_worklog
--     ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
-- ALTER TABLE chg_schema.t_change_attachment
--     ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
-- ALTER TABLE report_schema.statistics_snapshot
--     ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- ==========================================================
-- DELETE 阻止触发器（防御深度）
-- ==========================================================
-- 在上述 4 张表上安装 trg_v1004_prevent_hard_delete 触发器，
-- 复用 r197 已定义的 public.fn_prevent_hard_delete() 函数。
--
-- 效果: 任何 DELETE FROM <table> 在 DB 层被拒绝；
--       业务层必须改用 UPDATE is_deleted=true（MyBatis-Plus @TableLogic 自动转换）