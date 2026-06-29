-- Migration: 090_iec62304_checklist.sql
-- Date: 2026-06-02
-- Author: 朱涛
-- Reason: 创建 IEC 62304 合规检查清单表（FR-0.15 / US-9 后端 API 缺表）
-- Related: v1.19 / R19 / 变更6

CREATE TABLE IF NOT EXISTS compliance_schema.t_iec62304_checklist (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    clause_no VARCHAR(50) NOT NULL,
    clause_title VARCHAR(500) NOT NULL,
    section_title VARCHAR(200),
    section_order INT DEFAULT 0,
    clause_order INT DEFAULT 0,
    compliance_status VARCHAR(50) DEFAULT 'PENDING',  -- PENDING/COMPLIANT/PARTIAL/NON_COMPLIANT/NOT_APPLICABLE
    evidence TEXT,
    gaps TEXT,
    assessor_id BIGINT,
    assessor_name VARCHAR(100),
    assessed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (project_id, clause_no)
);

CREATE INDEX IF NOT EXISTS idx_iec_project ON compliance_schema.t_iec62304_checklist(project_id);
CREATE INDEX IF NOT EXISTS idx_iec_status ON compliance_schema.t_iec62304_checklist(compliance_status);
CREATE INDEX IF NOT EXISTS idx_iec_section ON compliance_schema.t_iec62304_checklist(project_id, section_order, clause_order);
