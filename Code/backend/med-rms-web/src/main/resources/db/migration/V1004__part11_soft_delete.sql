-- V1004: 21 CFR Part 11 §11.10(c) 软删除合规迁移
-- 提交日期: 2026-08-10
-- 关联代码修复: P1-4 (CODE_REVIEW_FULL_VERIFICATION_2026-08-10.md)
--
-- 背景:
-- 21 CFR Part 11 要求电子记录"保护以防删除"。
-- 此前 4 个核心实体缺失 is_deleted 列,Service 层 deleteById() 会执行硬删除,
-- 违反 11.10(c) 与 IEC 62304 §5.1.6。
--
-- 本迁移为以下 4 个表添加 is_deleted 列 + DELETE 阻止触发器:
--   1. prj_schema.t_task_predecessor  (甘特图任务前置依赖)
--   2. prj_schema.t_worklog          (工时记录 - 含 PII / 财务核算)
--   3. chg_schema.t_change_attachment (变更附件 - 含合规证据)
--   4. report_schema.statistics_snapshot (统计快照 - CQRS 读模型)
--
-- 同时应用 public.fn_prevent_hard_delete() 触发器（与 r197 模式一致）。
-- 与 r197 不同: 本次只在 4 张表上显式登记触发器（避免影响其他历史迁移）。

-- ==========================================================
-- 1. prj_schema.t_task_predecessor
-- ==========================================================
ALTER TABLE prj_schema.t_task_predecessor
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_task_predecessor_isdeleted
    ON prj_schema.t_task_predecessor(is_deleted);

COMMENT ON COLUMN prj_schema.t_task_predecessor.is_deleted
    IS 'V1004 21 CFR Part 11 §11.10(c) 软删除标志，业务层使用 UPDATE is_deleted=true 代替 DELETE';

-- ==========================================================
-- 2. prj_schema.t_worklog
-- ==========================================================
ALTER TABLE prj_schema.t_worklog
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_worklog_isdeleted
    ON prj_schema.t_worklog(is_deleted);

COMMENT ON COLUMN prj_schema.t_worklog.is_deleted
    IS 'V1004 21 CFR Part 11 §11.10(c) 软删除标志';

-- ==========================================================
-- 3. chg_schema.t_change_attachment
-- ==========================================================
ALTER TABLE chg_schema.t_change_attachment
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_change_attachment_isdeleted
    ON chg_schema.t_change_attachment(is_deleted);

COMMENT ON COLUMN chg_schema.t_change_attachment.is_deleted
    IS 'V1004 21 CFR Part 11 §11.10(c) 软删除标志（附件含合规证据）';

-- ==========================================================
-- 4. report_schema.statistics_snapshot
-- ==========================================================
ALTER TABLE report_schema.statistics_snapshot
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_statistics_snapshot_isdeleted
    ON report_schema.statistics_snapshot(is_deleted);

COMMENT ON COLUMN report_schema.statistics_snapshot.is_deleted
    IS 'V1004 21 CFR Part 11 §11.10(c) 软删除标志';

-- ==========================================================
-- 5. 通用 DELETE 阻止触发器（4 张表）
-- 复用 r197_compliance_triggers.sql 中已定义的 public.fn_prevent_hard_delete()
-- 业务层 UPDATE is_deleted=true 仍允许；DELETE 直接拒绝
-- ==========================================================

DO $$
DECLARE
    tbl_record RECORD;
    target_tables TEXT[] := ARRAY[
        'prj_schema.t_task_predecessor',
        'prj_schema.t_worklog',
        'chg_schema.t_change_attachment',
        'report_schema.statistics_snapshot'
    ];
    tbl_name TEXT;
BEGIN
    -- 检查依赖函数是否存在（r197 应已创建），不存在则创建
    IF NOT EXISTS (
        SELECT 1 FROM pg_proc p
        JOIN pg_namespace n ON p.pronamespace = n.oid
        WHERE p.proname = 'fn_prevent_hard_delete' AND n.nspname = 'public'
    ) THEN
        CREATE FUNCTION public.fn_prevent_hard_delete()
        RETURNS TRIGGER AS $fn$
        BEGIN
            RAISE EXCEPTION '禁止硬删除 (21 CFR Part 11 §11.10(c)): 请使用软删除 (UPDATE is_deleted=true)'
                USING HINT = '表 ' || TG_TABLE_SCHEMA || '.' || TG_TABLE_NAME || ' 设置了 DELETE 触发器';
        END;
        $fn$ LANGUAGE plpgsql;

        RAISE NOTICE 'V1004: 已创建 public.fn_prevent_hard_delete() 函数';
    END IF;

    -- 在 4 张表上安装 DELETE 阻止触发器
    FOREACH tbl_name IN ARRAY target_tables
    LOOP
        EXECUTE format(
            'DROP TRIGGER IF EXISTS trg_v1004_prevent_hard_delete ON %s; '
            'CREATE TRIGGER trg_v1004_prevent_hard_delete '
            '  BEFORE DELETE ON %s '
            '  FOR EACH ROW EXECUTE FUNCTION public.fn_prevent_hard_delete();',
            tbl_name, tbl_name
        );
        RAISE NOTICE 'V1004: 已为 %s 安装 DELETE 阻止触发器', tbl_name;
    END LOOP;
END $$;

-- ==========================================================
-- 6. 业务层清理建议（人工执行，不在 Flyway 内）
-- ==========================================================
-- 本迁移完成后,任何调用以下方法的代码都会在 DB 层被拒绝:
--   prj_schema.t_task_predecessor: DELETE FROM ...
--   prj_schema.t_worklog: DELETE FROM ...
--   chg_schema.t_change_attachment: DELETE FROM ...
--   report_schema.statistics_snapshot: DELETE FROM ...
--
-- 业务层应改为:
--   UPDATE <table> SET is_deleted = true WHERE id = ?
-- 或调用 MyBatis-Plus @TableLogic 字段自动转换 (Service.deleteById 仍走 UPDATE is_deleted=true)

-- ==========================================================
-- 7. 验证（迁移完成后人工执行）
-- ==========================================================
-- SELECT table_schema, table_name, column_name
-- FROM information_schema.columns
-- WHERE column_name = 'is_deleted'
--   AND table_schema || '.' || table_name IN (
--     'prj_schema.t_task_predecessor',
--     'prj_schema.t_worklog',
--     'chg_schema.t_change_attachment',
--     'report_schema.statistics_snapshot'
--   );
--
-- 预期返回 4 行。
--
-- SELECT trigger_name, event_object_schema, event_object_table
-- FROM information_schema.triggers
-- WHERE trigger_name = 'trg_v1004_prevent_hard_delete';
--
-- 预期返回 4 行。