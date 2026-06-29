-- Migration: 100_compliance_template.sql
-- Date: 2026-06-02
-- Author: 朱涛
-- Reason: 创建合规模板表（FR-1.9 / US-10）
-- Related: v1.19 / R19 / 变更7

CREATE TABLE IF NOT EXISTS proj_schema.t_compliance_template (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,  -- NMPA / ISO13485 / IEC62304 / FDA510K / CUSTOM
    name VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'PRESET',  -- PRESET / CUSTOM
    description TEXT,
    config_json TEXT,  -- JSON 配置：URS 预填/评审流程/DCP门限/法规关联等
    is_active BOOLEAN DEFAULT TRUE,
    created_by BIGINT,
    created_by_name VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tpl_type ON proj_schema.t_compliance_template(type) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_tpl_code ON proj_schema.t_compliance_template(code);

-- 项目表添加 template_id 字段（追溯项目使用的模板）
ALTER TABLE proj_schema.t_project ADD COLUMN IF NOT EXISTS template_id BIGINT;
ALTER TABLE proj_schema.t_project ADD COLUMN IF NOT EXISTS template_code VARCHAR(50);

COMMENT ON TABLE proj_schema.t_compliance_template IS '合规模板表 - FR-1.9';
COMMENT ON COLUMN proj_schema.t_project.template_id IS '创建项目时应用的合规模板 ID';
COMMENT ON COLUMN proj_schema.t_project.template_code IS '创建项目时应用的合规模板编号';
