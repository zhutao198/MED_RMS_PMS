-- ============================================================================
-- 140 | v1.55 修复：追溯缺口忽略记录表
-- TraceGaps.vue "忽略" 操作的去重持久化
-- ============================================================================

CREATE TABLE IF NOT EXISTS trace_schema.t_trace_gap_ignored (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL,
    gap_type        VARCHAR(50) NOT NULL,
    requirement_id  BIGINT NOT NULL,
    reason          VARCHAR(500),
    ignored_by      BIGINT,
    ignored_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE trace_schema.t_trace_gap_ignored IS '追溯缺口忽略记录（v1.55）';
COMMENT ON COLUMN trace_schema.t_trace_gap_ignored.project_id IS '项目 ID';
COMMENT ON COLUMN trace_schema.t_trace_gap_ignored.gap_type IS '缺口类型：MISSING_CHILDREN/ORPHAN/NO_TEST_CASE';
COMMENT ON COLUMN trace_schema.t_trace_gap_ignored.requirement_id IS '关联需求 ID';
COMMENT ON COLUMN trace_schema.t_trace_gap_ignored.reason IS '忽略原因';
COMMENT ON COLUMN trace_schema.t_trace_gap_ignored.ignored_by IS '忽略人';
COMMENT ON COLUMN trace_schema.t_trace_gap_ignored.ignored_at IS '忽略时间';

CREATE INDEX IF NOT EXISTS idx_gap_ignored_project
    ON trace_schema.t_trace_gap_ignored(project_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_gap_ignored_key
    ON trace_schema.t_trace_gap_ignored(project_id, gap_type, requirement_id);
