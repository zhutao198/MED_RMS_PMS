-- FR-2.9 工时填报记录表
CREATE TABLE IF NOT EXISTS prj_schema.t_worklog (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT,
    project_id BIGINT,
    requirement_id BIGINT,
    worker_id BIGINT NOT NULL,
    worker_name VARCHAR(100),
    work_date DATE NOT NULL,
    hours DECIMAL(5,2) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 修复: task_id 允许 NULL（通用工时填报不一定关联任务）
ALTER TABLE prj_schema.t_worklog ALTER COLUMN task_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_worklog_task ON prj_schema.t_worklog(task_id);
CREATE INDEX IF NOT EXISTS idx_worklog_project ON prj_schema.t_worklog(project_id);
CREATE INDEX IF NOT EXISTS idx_worklog_worker ON prj_schema.t_worklog(worker_id);
CREATE INDEX IF NOT EXISTS idx_worklog_date ON prj_schema.t_worklog(work_date);
