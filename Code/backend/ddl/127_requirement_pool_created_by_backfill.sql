-- ============================================================
-- 127_requirement_pool_created_by_backfill.sql
-- v1.40 需求池 createdBy 回填 — 历史数据修复
--
-- 背景：v1.40 深测需求池模块发现 10 条历史池条目 created_by 为 NULL
--        （id=59/53/52/51/50/44/43/42/41/40），addToPool 未从
--        SecurityContextHolder 取当前用户。
--
-- 修复：v1.40 后端 SecurityUtils.getCurrentUserId() 注入 +
--       本 DDL 回填历史 NULL 数据。
--
-- 回填策略：全部归 admin（user 1）— 历史种子数据由 admin 写入。
--
-- 可重复执行：WHERE created_by IS NULL 保护已正确数据。
-- ============================================================

UPDATE req_schema.t_requirement_pool
SET created_by = 1
WHERE created_by IS NULL
  AND id IN (40, 41, 42, 43, 44, 50, 51, 52, 53, 59);

-- 防御：还有漏网的 NULL → 一律归 admin
UPDATE req_schema.t_requirement_pool
SET created_by = 1
WHERE created_by IS NULL;
