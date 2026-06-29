-- FR-1.8 FMEA 在线编辑器字段补充
ALTER TABLE risk_schema.t_risk_assessment
    ADD COLUMN IF NOT EXISTS severity SMALLINT,
    ADD COLUMN IF NOT EXISTS occurrence SMALLINT,
    ADD COLUMN IF NOT EXISTS detection SMALLINT,
    ADD COLUMN IF NOT EXISTS rpn INTEGER,
    ADD COLUMN IF NOT EXISTS action_plan TEXT,
    ADD COLUMN IF NOT EXISTS action_owner VARCHAR(100),
    ADD COLUMN IF NOT EXISTS action_due_date DATE,
    ADD COLUMN IF NOT EXISTS action_status VARCHAR(30) DEFAULT 'OPEN';

CREATE INDEX IF NOT EXISTS idx_risk_rpn ON risk_schema.t_risk_assessment(rpn);
CREATE INDEX IF NOT EXISTS idx_risk_action_status ON risk_schema.t_risk_assessment(action_status);

COMMENT ON COLUMN risk_schema.t_risk_assessment.severity IS '严重度 S 1-10';
COMMENT ON COLUMN risk_schema.t_risk_assessment.occurrence IS '发生度 O 1-10';
COMMENT ON COLUMN risk_schema.t_risk_assessment.detection IS '探测度 D 1-10';
COMMENT ON COLUMN risk_schema.t_risk_assessment.rpn IS '风险优先数 RPN=S*O*D';
COMMENT ON COLUMN risk_schema.t_risk_assessment.action_plan IS '改进措施';
COMMENT ON COLUMN risk_schema.t_risk_assessment.action_owner IS '责任人';
COMMENT ON COLUMN risk_schema.t_risk_assessment.action_due_date IS '完成期限';
COMMENT ON COLUMN risk_schema.t_risk_assessment.action_status IS 'OPEN/IN_PROGRESS/COMPLETED';
