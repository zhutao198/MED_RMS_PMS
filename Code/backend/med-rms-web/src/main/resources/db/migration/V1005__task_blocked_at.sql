-- V1005: 任务阻塞起始时间字段（需求流转流程梳理.md 差异 #4）
-- 提交日期: 2026-08-12
-- 关联修复: 任务进入 BLOCKED 时记录 blocked_at，超 3 天未解除则通知 PM 并将需求标 Suspect
--
-- PRD §7.7.1 联动规则：任一任务"已阻塞"超 3 天 → 通知 PM + 需求标风险（Suspect）。
-- 此前 t_task 无阻塞起始时间，无法判断"超 3 天"，仅能立即标 Suspect（弱化实现）。
-- 本迁移新增 blocked_at 列，进入 BLOCKED 由业务层写入当前时间，解除时置 NULL。

ALTER TABLE proj_schema.t_task
    ADD COLUMN IF NOT EXISTS blocked_at TIMESTAMP;

COMMENT ON COLUMN proj_schema.t_task.blocked_at
    IS 'V1005 任务进入 BLOCKED 的起始时间，用于判断阻塞是否超 3 天（PRD §7.7.1）';

CREATE INDEX IF NOT EXISTS idx_task_blocked_at
    ON proj_schema.t_task(blocked_at);

-- ----------------------------------------------------------
-- 差异 #5：需求收集池解析态时间字段
-- PRD 状态机 PENDING→PARSED→CONVERTED，PARSED 为解析中间态，
-- 此前 parsed_at 字段缺失，无法记录解析完成时间。
-- ----------------------------------------------------------
ALTER TABLE req_schema.t_requirement_pool
    ADD COLUMN IF NOT EXISTS parsed_at TIMESTAMP;

COMMENT ON COLUMN req_schema.t_requirement_pool.parsed_at
    IS 'V1005 收集池条目解析完成时间（进入 PARSED 态时记录，差异 #5）';

-- ==========================================================
-- 验证（迁移完成后人工执行）
-- ==========================================================
-- SELECT column_name, data_type, is_nullable
-- FROM information_schema.columns
-- WHERE table_schema = 'proj_schema' AND table_name = 't_task'
--   AND column_name = 'blocked_at';
-- 预期返回 1 行（timestamp, YES）。
-- SELECT column_name FROM information_schema.columns
-- WHERE table_schema = 'req_schema' AND table_name = 't_requirement_pool'
--   AND column_name = 'parsed_at';
-- 预期返回 1 行。
