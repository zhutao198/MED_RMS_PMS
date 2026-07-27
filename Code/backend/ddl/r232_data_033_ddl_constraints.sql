-- R232.1 DATA-033：变更时间线 ON DELETE CASCADE → RESTRICT
-- 原因：物理删除父变更会连带删除完整生命周期记录，破坏 21 CFR Part 11 §11.10(c)
-- 审计证据保留要求
-- 父表 t_change_request 已采用软删除（is_deleted），物理删除应被禁止
-- 注：迁移前需确认没有历史数据违反此约束（理论上不应有，因为软删除已足够）

-- 1. 删除原 CASCADE 外键
ALTER TABLE chg_schema.t_change_timeline
    DROP CONSTRAINT IF EXISTS fk_timeline_change;

-- 2. 重建为 RESTRICT 外键（物理删除父变更会抛错）
ALTER TABLE chg_schema.t_change_timeline
    ADD CONSTRAINT fk_timeline_change
    FOREIGN KEY (change_id)
    REFERENCES chg_schema.t_change_request(id)
    ON DELETE RESTRICT;

-- R232.1 DATA-034：TraceGapIgnored 表补 FK 约束
-- 原 140_trace_gap_ignored.sql 没有 FK，导致孤儿记录（项目/需求/用户被删后忽略记录仍存在）

-- 1. 删除已存在的同名约束（如果有）
ALTER TABLE trace_schema.t_trace_gap_ignored
    DROP CONSTRAINT IF EXISTS fk_gap_ignored_project;
ALTER TABLE trace_schema.t_trace_gap_ignored
    DROP CONSTRAINT IF EXISTS fk_gap_ignored_requirement;
ALTER TABLE trace_schema.t_trace_gap_ignored
    DROP CONSTRAINT IF EXISTS fk_gap_ignored_user;

-- 2. 添加 FK 约束（跨 schema 注意：项目 schema 是 proj_schema）
-- 注：跨 schema FK 在 PG 上需要所有引用的表都在同一数据库（满足）
ALTER TABLE trace_schema.t_trace_gap_ignored
    ADD CONSTRAINT fk_gap_ignored_project
    FOREIGN KEY (project_id)
    REFERENCES proj_schema.t_project(id)
    ON DELETE RESTRICT;

ALTER TABLE trace_schema.t_trace_gap_ignored
    ADD CONSTRAINT fk_gap_ignored_requirement
    FOREIGN KEY (requirement_id)
    REFERENCES req_schema.t_requirement(id)
    ON DELETE RESTRICT;

ALTER TABLE trace_schema.t_trace_gap_ignored
    ADD CONSTRAINT fk_gap_ignored_user
    FOREIGN KEY (ignored_by)
    REFERENCES sys_schema.t_user(id)
    ON DELETE SET NULL;

COMMENT ON CONSTRAINT fk_gap_ignored_project ON trace_schema.t_trace_gap_ignored IS
    'R232.1: 项目被物理删除时阻止（应软删除 is_deleted=true）';
COMMENT ON CONSTRAINT fk_gap_ignored_requirement ON trace_schema.t_trace_gap_ignored IS
    'R232.1: 需求被物理删除时阻止（应软删除 is_deleted=true）';
COMMENT ON CONSTRAINT fk_gap_ignored_user ON trace_schema.t_trace_gap_ignored IS
    'R232.1: 用户被删除时设为 NULL（保留忽略记录但解除用户关联）';

SELECT 'R232.1 DDL 安全约束修复完成' AS status;