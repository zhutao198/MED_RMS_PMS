-- R175: 模板分类扩展 (G7) + 项目预算告警字段 (G5)
ALTER TABLE proj_schema.t_compliance_template
    ADD COLUMN IF NOT EXISTS category VARCHAR(20) DEFAULT 'COMPLIANCE';

COMMENT ON COLUMN proj_schema.t_compliance_template.category IS '模板分类：COMPLIANCE/REQUIREMENT/REVIEW/PROJECT';

ALTER TABLE proj_schema.t_project
    ADD COLUMN IF NOT EXISTS budget_alarm_pct INTEGER DEFAULT 120;

COMMENT ON COLUMN proj_schema.t_project.budget_alarm_pct IS '工时预算告警阈值百分比，默认120';
