-- Med-RMS DDL补充 - 甘特图、里程碑、任务管理

-- 里程碑表
CREATE TABLE IF NOT EXISTS prj_schema.t_milestone (
    id BIGSERIAL PRIMARY KEY,
    milestone_no VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    project_id BIGINT NOT NULL,
    gate_type VARCHAR(20), -- DCP1/DCP2/DCP3/DCP4/DCP5
    planned_date DATE,
    actual_date DATE,
    status VARCHAR(20) DEFAULT 'PLANNED', -- PLANNED/IN_PROGRESS/COMPLETED/DELAYED
    check_result VARCHAR(20), -- PASS/FAIL/PENDING
    check_comments TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_milestone_project ON prj_schema.t_milestone(project_id);
CREATE INDEX idx_milestone_gate ON prj_schema.t_milestone(gate_type);

-- 任务表
CREATE TABLE IF NOT EXISTS prj_schema.t_task (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    project_id BIGINT NOT NULL,
    assignee_id BIGINT,
    assignee_name VARCHAR(100),
    parent_task_id BIGINT, -- 父任务
    start_date DATE,
    end_date DATE,
    estimated_hours INT,
    actual_hours INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'TODO', -- TODO/IN_PROGRESS/IN_TEST/DONE/BLOCKED
    priority VARCHAR(20) DEFAULT 'MEDIUM', -- HIGH/MEDIUM/LOW
    requirement_id BIGINT, -- 关联的需求ID
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_task_project ON prj_schema.t_task(project_id);
CREATE INDEX idx_task_assignee ON prj_schema.t_task(assignee_id);
CREATE INDEX idx_task_status ON prj_schema.t_task(status);
CREATE INDEX idx_task_requirement ON prj_schema.t_task(requirement_id);

-- 需求收集池表
CREATE TABLE IF NOT EXISTS req_schema.t_requirement_pool (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(50) NOT NULL, -- CUSTOMER/MARKET/REGULATION/INTERNAL/COMPETITOR
    source_no VARCHAR(100), -- 原始需求编号/法规条款号
    raw_description TEXT, -- 原始需求描述
    title VARCHAR(200), -- 解析后标题
    parsed_description TEXT, -- 解析后描述
    priority VARCHAR(20), -- MoSCoW: MUST/SHOULD/COULD/WONT
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING/PARSED/CONVERTED/REJECTED
    project_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    converted_to_id BIGINT, -- 转换后的URS ID
    conversion_notes TEXT
);

CREATE INDEX idx_pool_source ON req_schema.t_requirement_pool(source);
CREATE INDEX idx_pool_status ON req_schema.t_requirement_pool(status);
CREATE INDEX idx_pool_project ON req_schema.t_requirement_pool(project_id);