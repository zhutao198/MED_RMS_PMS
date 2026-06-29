-- 支撑域 P0 修复：报表/Dashboard/Statistics CQRS Lite 表
-- 详细设计: 支撑域与通用域-详细设计.md §3.2 §3
-- 修复: 详细设计偏差分析报告 §3.6 #4
-- 日期: 2026-06-06 (修订 2026-06-08)

SET search_path TO report_schema;

-- 1. dashboard_config 用户仪表盘配置
CREATE TABLE IF NOT EXISTS dashboard_config (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    layout_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    widgets_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_dashboard_user ON dashboard_config(user_id);
CREATE INDEX IF NOT EXISTS idx_dashboard_default ON dashboard_config(user_id, is_default);

-- 2. statistics_snapshot CQRS Lite 读模型
CREATE TABLE IF NOT EXISTS statistics_snapshot (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    metric_type VARCHAR(50) NOT NULL,
    metric_key VARCHAR(100) NOT NULL,
    metric_value NUMERIC(18,4) NOT NULL DEFAULT 0,
    dimension_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_snapshot_project_type ON statistics_snapshot(project_id, metric_type);
CREATE INDEX IF NOT EXISTS idx_snapshot_calculated ON statistics_snapshot(calculated_at DESC);

-- 3. report_template 报表模板元数据
CREATE TABLE IF NOT EXISTS report_template (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(50) NOT NULL,
    template_config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_template_type ON report_template(type);
CREATE INDEX IF NOT EXISTS idx_template_active ON report_template(is_active);

-- 4. 初始化默认报表模板
INSERT INTO report_template (name, type, template_config_json, description) VALUES
('需求追溯矩阵', 'TRACEABILITY', '{"columns":["reqCode","title","type","status","testCount"],"groupBy":"type"}'::jsonb, '需求-测试用例追溯矩阵'),
('变更控制报告', 'CHANGE', '{"columns":["crCode","title","urgency","status","approvedAt"],"filters":{"status":"Approved"}}'::jsonb, '已批准变更控制报告'),
('合规检查报告', 'COMPLIANCE', '{"columns":["checkType","checkName","result","checkedAt"],"groupBy":"checkType"}'::jsonb, '合规检查汇总'),
('风险评估报告', 'RISK', '{"columns":["riskCode","title","severity","probability","rpn","riskLevel"]}'::jsonb, 'FMEA 风险评估报告')
ON CONFLICT DO NOTHING;

-- 5. 初始化默认仪表盘布局 (user_id=0 代表系统默认)
INSERT INTO dashboard_config (user_id, layout_json, widgets_json, is_default) VALUES
(0, '[{"i":"requirements","x":0,"y":0,"w":6,"h":4},{"i":"risk","x":6,"y":0,"w":6,"h":4},{"i":"compliance","x":0,"y":4,"w":6,"h":4},{"i":"management","x":6,"y":4,"w":6,"h":4}]'::jsonb,
 '["requirements","risk","compliance","management"]'::jsonb, TRUE)
ON CONFLICT DO NOTHING;
