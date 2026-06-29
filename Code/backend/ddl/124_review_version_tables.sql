-- =========================================================================
-- 124_review_version_tables.sql
-- 补充 v1.27 DDL 同步核查发现的 2 张缺失表
-- 实体类存在但 DDL 文件中无对应 CREATE TABLE
-- =========================================================================

-- 需求评审记录
CREATE TABLE IF NOT EXISTS req_schema.t_review (
    id              BIGSERIAL PRIMARY KEY,
    requirement_id  BIGINT NOT NULL,
    reviewer_id     BIGINT,
    decision        VARCHAR(32),
    comments        TEXT,
    reviewed_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_review_requirement ON req_schema.t_review(requirement_id);
CREATE INDEX IF NOT EXISTS idx_review_reviewer    ON req_schema.t_review(reviewer_id);

-- 需求版本历史
CREATE TABLE IF NOT EXISTS req_schema.t_requirement_version (
    id              BIGSERIAL PRIMARY KEY,
    requirement_id  BIGINT NOT NULL,
    version_no      INTEGER NOT NULL,
    snapshot        TEXT,
    change_summary  TEXT,
    changed_by      BIGINT,
    changed_at      TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_req_version_requirement ON req_schema.t_requirement_version(requirement_id);
CREATE INDEX IF NOT EXISTS idx_req_version_no          ON req_schema.t_requirement_version(requirement_id, version_no);

-- 注释
COMMENT ON TABLE req_schema.t_review IS '需求评审记录 (v1.27 DDL 同步补)';
COMMENT ON TABLE req_schema.t_requirement_version IS '需求版本历史 (v1.27 DDL 同步补)';
