-- =========================================================================
-- 130_trace_link_table.sql
-- v1.47 BUG #133 P0 修复：通用追溯链接表（拆分纵/横）
-- 替代原来 RequirementRelation 混用纵/横的设计
-- =========================================================================

CREATE TABLE IF NOT EXISTS trace_schema.t_trace_link (
    id              BIGSERIAL PRIMARY KEY,
    link_type       VARCHAR(32) NOT NULL,                  -- DECOMPOSE/REFINES/DEPENDS/CONFLICTS/REUSES/VERIFIES
    source_type     VARCHAR(32) NOT NULL DEFAULT 'REQUIREMENT',  -- REQUIREMENT/TEST_CASE
    source_id       BIGINT NOT NULL,
    source_no       VARCHAR(64),
    target_type     VARCHAR(32) NOT NULL DEFAULT 'REQUIREMENT',
    target_id       BIGINT NOT NULL,
    target_no       VARCHAR(64),
    project_id      BIGINT,
    trace_context   TEXT,
    created_by      BIGINT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted      BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_tracelink_source     ON trace_schema.t_trace_link(source_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_tracelink_target     ON trace_schema.t_trace_link(target_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_tracelink_project    ON trace_schema.t_trace_link(project_id, link_type, is_deleted);
CREATE INDEX IF NOT EXISTS idx_tracelink_pair       ON trace_schema.t_trace_link(source_id, target_id, link_type);

-- 防重复：同一 source/target/type 只能存在一条（删除/失效的不算）
CREATE UNIQUE INDEX IF NOT EXISTS uk_tracelink_active
    ON trace_schema.t_trace_link(source_id, target_id, link_type)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE trace_schema.t_trace_link IS '通用追溯链接（v1.47 BUG #133 拆分纵/横）';
