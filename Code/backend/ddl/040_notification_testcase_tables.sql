-- Med-RMS DDL补充 - notification和testcase模块

-- 通知表
CREATE TABLE IF NOT EXISTS not_schema.t_notification (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(100),
    title VARCHAR(200) NOT NULL,
    content TEXT,
    type VARCHAR(50) NOT NULL, -- REVIEW_REJECTED/TRACE_BROKEN/RISK_ALERT/CHANGE_APPROVED/SYSTEM
    status VARCHAR(20) DEFAULT 'UNREAD', -- UNREAD/READ
    source_type VARCHAR(50), -- REVIEW/REQUIREMENT/CHANGE/RISK
    source_id BIGINT,
    read_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_user ON not_schema.t_notification(user_id);
CREATE INDEX idx_notification_status ON not_schema.t_notification(status);
CREATE INDEX idx_notification_type ON not_schema.t_notification(type);

-- 测试用例表 (补充遗漏的requirement关联字段)
ALTER TABLE req_schema.t_test_case ADD COLUMN IF NOT EXISTS requirement_id BIGINT;
ALTER TABLE req_schema.t_test_case ADD COLUMN IF NOT EXISTS requirement_no VARCHAR(50);
ALTER TABLE req_schema.t_test_case ADD COLUMN IF NOT EXISTS version INT DEFAULT 1;
ALTER TABLE req_schema.t_test_case ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_testcase_requirement ON req_schema.t_test_case(requirement_id);

-- 基线表补充字段（如果需要存储快照JSON）
ALTER TABLE compliance_schema.t_baseline ADD COLUMN IF NOT EXISTS snapshot_data JSONB;