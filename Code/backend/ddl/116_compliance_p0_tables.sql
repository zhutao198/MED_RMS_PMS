-- v1.47 BUG #119/#121 P0 修复：合规管理 P0 表
-- 1) SafetyClassification（IEC 62304 §5）
-- 2) PrCorrection（ISO 13485 §8.5.2 CAPA）

CREATE TABLE IF NOT EXISTS compliance_schema.t_safety_classification (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    safety_class VARCHAR(16) NOT NULL,
    classification_rationale TEXT,
    classified_by BIGINT,
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    remarks TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_safety_class_project ON compliance_schema.t_safety_classification(project_id, status);

CREATE TABLE IF NOT EXISTS compliance_schema.t_pr_correction (
    id BIGSERIAL PRIMARY KEY,
    problem_report_id BIGINT NOT NULL,
    action TEXT NOT NULL,
    owner_id BIGINT,
    due_date TIMESTAMP,
    completed_at TIMESTAMP,
    verified_by BIGINT,
    verified_at TIMESTAMP,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    effectiveness TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_pr_correction_report ON compliance_schema.t_pr_correction(problem_report_id, status);
