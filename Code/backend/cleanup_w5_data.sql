-- 清理 W5/W12 压测和烟测遗留需求
BEGIN;

-- 删除模式：压测需求 / 纯数字标题 / SMOKE- / REQ-AUTO- / NFR- / PERF- / LOAD- 等
WITH deleted AS (
    DELETE FROM req_schema.t_requirement
    WHERE title ~ '^(压测需求 [0-9]+|[0-9]+$|SMOKE-|REQ-AUTO-|NFR-|PERF-|LOAD-|P0-[0-9]+|BULK-[0-9]+)'
       OR title IN ('TBD', 'test', 'placeholder', 'sample', 'demo')
    RETURNING id
)
SELECT 'deleted_misc' AS op, COUNT(*) AS n FROM deleted;

-- 验证剩余
SELECT 'remaining_total' AS check_name, COUNT(*) AS n FROM req_schema.t_requirement
UNION ALL
SELECT 'draft_remaining', COUNT(*) FROM req_schema.t_requirement WHERE status = 'Draft'
UNION ALL
SELECT 'P1_remaining', COUNT(*) FROM req_schema.t_requirement WHERE priority = 'P1';

COMMIT;
