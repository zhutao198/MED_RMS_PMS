-- Med-RMS DDL补充 - risk和project模块

-- 项目表
CREATE TABLE IF NOT EXISTS prj_schema.t_project (
    id BIGSERIAL PRIMARY KEY,
    project_no VARCHAR(50) NOT NULL UNIQUE,
    project_name VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(50) DEFAULT 'PLANNING', -- PLANNING/IN_PROGRESS/COMPLETED/TERMINATED
    manager_id BIGINT,
    manager_name VARCHAR(100),
    start_date DATE,
    end_date DATE,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_project_status ON prj_schema.t_project(status);
CREATE INDEX idx_project_manager ON prj_schema.t_project(manager_id);

-- 风险评估表
CREATE TABLE IF NOT EXISTS risk_schema.t_risk_assessment (
    id BIGSERIAL PRIMARY KEY,
    requirement_id BIGINT NOT NULL,
    risk_level VARCHAR(20) NOT NULL, -- HIGH/MEDIUM/LOW
    hazard_level VARCHAR(50) NOT NULL, -- CATASTROPHIC/CRITICAL/MAJOR/MINOR/NEGLIGIBLE
    risk_score DECIMAL(10,2),
    hazard_source VARCHAR(500),
    hazard_situation VARCHAR(1000),
    harm VARCHAR(500),
    control_measure VARCHAR(2000),
    residual_risk VARCHAR(50), -- ACCEPTABLE/UNACCEPTABLE/ALARP
    risk_status VARCHAR(50) DEFAULT 'OPEN', -- OPEN/CLOSED/MONITORING
    assessed_by BIGINT,
    assessed_at TIMESTAMP,
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_risk_requirement ON risk_schema.t_risk_assessment(requirement_id);
CREATE INDEX idx_risk_level ON risk_schema.t_risk_assessment(risk_level);
CREATE INDEX idx_risk_status ON risk_schema.t_risk_assessment(risk_status);

-- 报表记录表
CREATE TABLE IF NOT EXISTS rpt_schema.t_report (
    id BIGSERIAL PRIMARY KEY,
    report_type VARCHAR(50) NOT NULL, -- TRACEABILITY/CHANGE/COMPLIANCE/RISK
    title VARCHAR(200) NOT NULL,
    project_id BIGINT,
    file_path VARCHAR(500),
    generated_by BIGINT,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_report_type ON rpt_schema.t_report(report_type);
CREATE INDEX idx_report_project ON rpt_schema.t_report(project_id);