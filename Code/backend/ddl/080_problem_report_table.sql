-- Migration: 080_problem_report_table.sql
-- Date: 2026-06-02
-- Author: 朱涛
-- Reason: 创建问题报告表（FR-0.14 后端 API 一直未测试，发现表不存在）
-- Related: v1.19 / R19 / 变更5

CREATE TABLE IF NOT EXISTS compliance_schema.t_problem_report (
    id BIGSERIAL PRIMARY KEY,
    report_code VARCHAR(50) UNIQUE NOT NULL,
    project_id BIGINT,
    project_name VARCHAR(200),
    title VARCHAR(500) NOT NULL,
    severity VARCHAR(20),  -- CRITICAL/MAJOR/MINOR
    description TEXT,
    status VARCHAR(50) DEFAULT 'Open',  -- Open/Analyzing/Correcting/Verifying/Closed
    discovery_date TIMESTAMP,
    source_type VARCHAR(50),  -- internal/external/regulatory
    affected_items TEXT,
    reporter_id BIGINT,
    reporter_name VARCHAR(100),
    assignee_id BIGINT,
    assignee_name VARCHAR(100),
    resolved_at TIMESTAMP,
    resolution TEXT,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pr_project ON compliance_schema.t_problem_report(project_id) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_pr_status ON compliance_schema.t_problem_report(status) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_pr_severity ON compliance_schema.t_problem_report(severity) WHERE is_deleted = FALSE;
