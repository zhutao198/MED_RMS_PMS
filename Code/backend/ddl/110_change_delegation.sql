-- ============================================================================
-- 110 | FR-1.7 变更审批流 - 委派 + 会签
-- 为 chg_schema.t_change_request 添加委派/会签相关字段
-- ============================================================================

ALTER TABLE chg_schema.t_change_request
    ADD COLUMN IF NOT EXISTS assignee_id BIGINT,
    ADD COLUMN IF NOT EXISTS assignee_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS delegated_from_id BIGINT,
    ADD COLUMN IF NOT EXISTS delegated_from_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS delegated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS countersign_required BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS countersigners JSONB,
    ADD COLUMN IF NOT EXISTS countersign_progress VARCHAR(20) DEFAULT 'NONE';

COMMENT ON COLUMN chg_schema.t_change_request.assignee_id IS '当前受派人 ID（默认 = requester_id/审批人）';
COMMENT ON COLUMN chg_schema.t_change_request.assignee_name IS '当前受派人姓名';
COMMENT ON COLUMN chg_schema.t_change_request.delegated_from_id IS '委派来源人 ID（记录委派审计）';
COMMENT ON COLUMN chg_schema.t_change_request.delegated_from_name IS '委派来源人姓名';
COMMENT ON COLUMN chg_schema.t_change_request.delegated_at IS '委派时间';
COMMENT ON COLUMN chg_schema.t_change_request.countersign_required IS '是否需要会签（MAJOR/EMERGENCY 默认 true）';
COMMENT ON COLUMN chg_schema.t_change_request.countersigners IS '会签人列表 JSON: [{id,name,signed,signedAt,comments}]';
COMMENT ON COLUMN chg_schema.t_change_request.countersign_progress IS '会签进度：NONE/PENDING/PARTIAL/COMPLETED';

CREATE INDEX IF NOT EXISTS idx_change_assignee
    ON chg_schema.t_change_request(assignee_id)
    WHERE assignee_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_change_progress
    ON chg_schema.t_change_request(countersign_progress)
    WHERE countersign_required = TRUE;
