-- R92 迁移：项目交付物表（替代 R84 临时挂"功能开发中"提示的 ProjectDeliverables 页面）
CREATE TABLE IF NOT EXISTS proj_schema.t_project_deliverable (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(50),
    phase VARCHAR(100),
    status VARCHAR(30) DEFAULT 'TODO',
    owner_id BIGINT,
    owner_name VARCHAR(100),
    due_date DATE,
    description TEXT,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_deliverable_project ON proj_schema.t_project_deliverable(project_id) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_deliverable_status ON proj_schema.t_project_deliverable(status) WHERE is_deleted = FALSE;

COMMENT ON TABLE proj_schema.t_project_deliverable IS '项目交付物登记（FR 配套）';
COMMENT ON COLUMN proj_schema.t_project_deliverable.type IS '类型: DOC 文档 / CODE 代码 / TEST_CASE 测试用例 / REPORT 报告 / BASELINE 基线';
COMMENT ON COLUMN proj_schema.t_project_deliverable.status IS '状态: TODO 待开始 / IN_PROGRESS 进行中 / DONE 已完成 / BLOCKED 阻塞';
