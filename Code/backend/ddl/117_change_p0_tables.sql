-- v1.47 BUG #110/#111 P0 修复：变更管理 P0 表
-- 1) ChangeApproval（每次审批记录）
-- 2) ChangeExecution（执行时受影响需求版本快照）

CREATE TABLE IF NOT EXISTS chg_schema.t_change_approval (
    id BIGSERIAL PRIMARY KEY,
    change_id BIGINT NOT NULL,
    approver_id BIGINT,
    approver_name VARCHAR(64),
    decision VARCHAR(16) NOT NULL,
    comments TEXT,
    signature_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_chg_approval_change ON chg_schema.t_change_approval(change_id, created_at);

CREATE TABLE IF NOT EXISTS chg_schema.t_change_execution (
    id BIGSERIAL PRIMARY KEY,
    change_id BIGINT NOT NULL,
    requirement_id BIGINT,
    requirement_no VARCHAR(64),
    old_version VARCHAR(32),
    new_version VARCHAR(32),
    old_snapshot TEXT,
    new_snapshot TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'EXECUTING',
    executor_id BIGINT,
    executor_name VARCHAR(64),
    executed_at TIMESTAMP,
    completed_at TIMESTAMP,
    remarks TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_chg_exec_change ON chg_schema.t_change_execution(change_id);
CREATE INDEX IF NOT EXISTS idx_chg_exec_req ON chg_schema.t_change_execution(requirement_id);
