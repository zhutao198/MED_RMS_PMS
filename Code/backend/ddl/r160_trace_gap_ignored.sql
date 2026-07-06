-- =============================================================================
-- R160 修复：F6 /traceability/gaps/ignored SY0000 异常
-- 根因：TraceGapIgnored entity @TableName("trace_schema.t_trace_gap_ignored")
--       但表从未在 DDL 中创建过，mapper 查询时 relation not found 抛异常
-- 修复：建表 + 索引
-- =============================================================================

CREATE TABLE IF NOT EXISTS trace_schema.t_trace_gap_ignored (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL,
    gap_type        VARCHAR(50) NOT NULL,
    requirement_id  BIGINT NOT NULL,
    reason          TEXT,
    ignored_by      BIGINT,
    ignored_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_trace_gap_ignored_key UNIQUE (project_id, gap_type, requirement_id)
);

CREATE INDEX IF NOT EXISTS idx_trace_gap_ignored_project
    ON trace_schema.t_trace_gap_ignored(project_id, is_deleted)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE trace_schema.t_trace_gap_ignored IS 'R160: 追溯缺口忽略记录（v1.55 引入但表未创建）';
COMMENT ON COLUMN trace_schema.t_trace_gap_ignored.gap_type IS 'MISSING_CHILDREN/ORPHAN/NO_TEST_CASE';

-- 验证
SELECT 'R160 F6 验证' AS check, count(*) AS table_exists
FROM information_schema.tables
WHERE table_schema='trace_schema' AND table_name='t_trace_gap_ignored';