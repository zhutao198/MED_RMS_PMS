-- R222.2: 清理 test_r222_task_assignee_e2e.py e2e 残留数据
-- 范围: req_schema.t_requirement 的 SRS-101-080/081（id=98/99），status="InProgress"
-- 注: SRS-101-081 已被用户实测时尝试转化受阻"R222 已存在 1 个任务"
-- 操作: 软删 is_deleted=true + status='Closed'，保留 21 CFR 审计链完整可追溯

BEGIN;

-- 1. 软删需求 id=98/99（同步 task is_deleted 防悬挂引用）
UPDATE req_schema.t_requirement
   SET is_deleted = TRUE,
       status = 'Closed',
       title = title || ' [R222-e2e-residual]'
 WHERE id IN (98, 99)
   AND is_deleted = FALSE;

-- 2. 软删关联 Task id=15/16（同步 is_deleted + 清空 assigneeName 标记为 e2e 残留）
UPDATE proj_schema.t_task
   SET is_deleted = TRUE,
       assignee_name = COALESCE(assignee_name, '') || ' [R222-e2e-residual]'
 WHERE requirement_id IN (98, 99)
   AND is_deleted = FALSE;

-- 3. 验证
SELECT id, requirement_no, status, is_deleted, title
  FROM req_schema.t_requirement
 WHERE id IN (98, 99);

SELECT id, task_no, is_deleted, requirement_id
  FROM proj_schema.t_task
 WHERE requirement_id IN (98, 99);

COMMIT;
