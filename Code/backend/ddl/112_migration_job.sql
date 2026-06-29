-- FR-1.13 数据迁移任务表
CREATE TABLE IF NOT EXISTS admin_schema.t_migration_job (
    id BIGSERIAL PRIMARY KEY,
    job_name VARCHAR(200) NOT NULL,
    job_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'RUNNING',
    operator_id BIGINT,
    total_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    failure_count INT DEFAULT 0,
    error_log TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_migration_status ON admin_schema.t_migration_job(status);
CREATE INDEX IF NOT EXISTS idx_migration_type ON admin_schema.t_migration_job(job_type);
