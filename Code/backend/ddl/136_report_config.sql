-- DDL 136: 报表配置持久化（v1.46 P1-后端-1）
-- 修复 ReportsCustom.vue 之前用 /reports/generate 折中保存的问题：
-- 新建独立报表配置表，存储字段选择/筛选条件/项目/类型，支持命名复用与分享
-- 2026-06-08

CREATE TABLE IF NOT EXISTS compliance_schema.t_report_config (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    report_type VARCHAR(50) NOT NULL,           -- req/change/review/risk/project
    project_id BIGINT,                          -- 可空：全局报表不带项目
    fields_json TEXT NOT NULL,                  -- ["id","requirementNo","title","level",...]
    filters_json TEXT,                          -- {status:[...], priority:[...], dateRange:{...}}
    created_by BIGINT,
    created_by_name VARCHAR(64),
    is_shared BOOLEAN NOT NULL DEFAULT FALSE,   -- 是否全局共享
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_report_config_name UNIQUE (name, created_by, is_deleted)
);

CREATE INDEX IF NOT EXISTS idx_report_config_type
    ON compliance_schema.t_report_config(report_type) WHERE is_deleted = false;
CREATE INDEX IF NOT EXISTS idx_report_config_project
    ON compliance_schema.t_report_config(project_id) WHERE is_deleted = false;
CREATE INDEX IF NOT EXISTS idx_report_config_creator
    ON compliance_schema.t_report_config(created_by) WHERE is_deleted = false;

COMMENT ON TABLE compliance_schema.t_report_config IS '自定义报表配置（v1.46 P1-后端-1，ReportsCustom 持久化载体）';
COMMENT ON COLUMN compliance_schema.t_report_config.fields_json IS 'JSON 数组：选中的字段名';
COMMENT ON COLUMN compliance_schema.t_report_config.filters_json IS 'JSON 对象：状态/优先级/日期范围等筛选条件';
COMMENT ON COLUMN compliance_schema.t_report_config.is_shared IS '是否共享给同项目其他用户';
