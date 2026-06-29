-- ============================================================
-- 126_requirement_created_by_backfill.sql
-- v1.39 后端 createdBy/updatedBy 写入修复 — 历史数据回填
--
-- 背景：v1.38 资源管理深挖发现 141 条 project 1 + 138 条 project 8 +
--         1 条 project 14 的需求 created_by 为 NULL（createRequirement
--         未从 SecurityContextHolder 取当前用户），导致资源管理负载
--         计算（基于 createdBy/updatedBy）显示全 0。
--
-- 修复：前端修复（v1.38 BUG #24 已用 createdBy/updatedBy）+ 后端修复
--       （v1.39 SecurityUtils.getCurrentUserId() + 3 个 Service 注入）
--       + 本 DDL 回填历史 NULL 数据。
--
-- 回填策略（按项目实际管理者分配）：
--   project_id=1 (心电监护仪 v3.0)         manager_id=1 → user 1
--   project_id=8 (E180 ECG Device)        manager_id=NULL → user 1 (admin)
--   project_id=14 (smoke PRJ-000009)      manager_id=1 → user 1
-- updated_by 同步设为 created_by（无修改历史则默认等于创建人）。
--
-- 可重复执行：WHERE created_by IS NULL 保护已正确数据。
-- ============================================================

UPDATE req_schema.t_requirement
SET created_by = 1,
    updated_by = 1
WHERE created_by IS NULL
  AND project_id IN (1, 8, 14);

-- 防御：还有漏网的非空 project 且 created_by NULL → 一律归 admin
UPDATE req_schema.t_requirement
SET created_by = 1,
    updated_by = 1
WHERE created_by IS NULL
  AND project_id NOT IN (1, 8, 14);

-- 兜底：updated_by NULL 但 created_by 非空 → 等于 created_by
-- （历史种子数据只设了 created_by 未设 updated_by）
UPDATE req_schema.t_requirement
SET updated_by = created_by
WHERE updated_by IS NULL
  AND created_by IS NOT NULL;
