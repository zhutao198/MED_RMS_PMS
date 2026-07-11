-- R175 新增：项目活动流表（FR-2.13）
CREATE TABLE IF NOT EXISTS proj_schema.t_project_activity (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    activity_type VARCHAR(50) NOT NULL,  -- TASK_CREATED/TASK_STATUS_CHANGED/MILESTONE_UPDATED/RESOURCE_CHANGED/REQUIREMENT_LINKED/GANTT_CHANGED/PROJECT_CONFIG_CHANGED
    summary VARCHAR(255) NOT NULL,        -- 简要描述，如"张三 将任务 '开发登录' 从 TODO 变更为 IN_PROGRESS"
    detail TEXT,                           -- 变更前后的详细对比（JSON）
    operator_id BIGINT,
    operator_name VARCHAR(100),
    source_type VARCHAR(50),              -- 关联对象类型: TASK/MILESTONE/PROJECT/MEMBER
    source_id BIGINT,                     -- 关联对象ID
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_activity_project ON proj_schema.t_project_activity(project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_activity_type ON proj_schema.t_project_activity(activity_type);

COMMENT ON TABLE proj_schema.t_project_activity IS '项目活动流（FR-2.13）';
COMMENT ON COLUMN proj_schema.t_project_activity.activity_type IS '活动类型';
COMMENT ON COLUMN proj_schema.t_project_activity.summary IS '简要描述';
COMMENT ON COLUMN proj_schema.t_project_activity.detail IS '变更详情JSON';
