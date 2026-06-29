-- ============================================================================
-- Med-RMS 数据库初始化脚本
-- 数据库名：med_rms_pms
-- 使用方法：psql -U postgres -d med_rms_pms -f init_database.sql
-- ============================================================================

-- 1. 创建数据库（如果不存在）
CREATE DATABASE med_rms_pms WITH ENCODING 'UTF8' TEMPLATE template0;

-- 2. 连接数据库后执行以下脚本
\c med_rms_pms;

-- ============================================================================
-- 3. Schema 创建（9个限界上下文）
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS req_schema;
CREATE SCHEMA IF NOT EXISTS trace_schema;
CREATE SCHEMA IF NOT EXISTS chg_schema;
CREATE SCHEMA IF NOT EXISTS compliance_schema;
CREATE SCHEMA IF NOT EXISTS esign_schema;
CREATE SCHEMA IF NOT EXISTS risk_schema;
CREATE SCHEMA IF NOT EXISTS proj_schema;
CREATE SCHEMA IF NOT EXISTS report_schema;
CREATE SCHEMA IF NOT EXISTS sys_schema;
CREATE SCHEMA IF NOT EXISTS not_schema;

COMMENT ON SCHEMA req_schema        IS '需求管理限界上下文';
COMMENT ON SCHEMA trace_schema      IS '追溯管理限界上下文';
COMMENT ON SCHEMA chg_schema        IS '变更管理限界上下文';
COMMENT ON SCHEMA compliance_schema IS '合规管理限界上下文';
COMMENT ON SCHEMA esign_schema      IS '电子签名限界上下文';
COMMENT ON SCHEMA risk_schema       IS '风险管理限界上下文';
COMMENT ON SCHEMA proj_schema       IS '项目管理限界上下文';
COMMENT ON SCHEMA report_schema     IS '报表仪表盘限界上下文';
COMMENT ON SCHEMA sys_schema        IS '系统管理限界上下文';
COMMENT ON SCHEMA not_schema        IS '通知管理限界上下文';

-- ============================================================================
-- 4. 需求管理模块表
-- ============================================================================

CREATE SEQUENCE IF NOT EXISTS req_schema.seq_req_code START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 20;

CREATE TABLE IF NOT EXISTS req_schema.t_requirement (
    id BIGSERIAL PRIMARY KEY,
    requirement_no VARCHAR(50) NOT NULL UNIQUE,
    requirement_type VARCHAR(20) NOT NULL, -- URS/PRS/SRS/DRS
    project_id BIGINT,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    priority VARCHAR(20) NOT NULL, -- MUST/SHOULD/COULD/WONT
    risk_level VARCHAR(20),
    safety_class VARCHAR(10), -- A/B/C
    status VARCHAR(50) DEFAULT 'Draft',
    parent_id BIGINT,
    source VARCHAR(50),
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS req_schema.t_test_case (
    id BIGSERIAL PRIMARY KEY,
    test_case_no VARCHAR(50) NOT NULL UNIQUE,
    test_case_name VARCHAR(200) NOT NULL,
    test_type VARCHAR(50),
    test_method VARCHAR(50),
    requirement_id BIGINT,
    requirement_no VARCHAR(50),
    project_id BIGINT,
    version INT DEFAULT 1,
    status VARCHAR(50) DEFAULT 'DRAFT',
    description TEXT,
    pre_condition TEXT,
    test_steps TEXT,
    expected_result TEXT,
    safety_class VARCHAR(10),
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_req_no ON req_schema.t_requirement(requirement_no);
CREATE INDEX IF NOT EXISTS idx_req_type ON req_schema.t_requirement(requirement_type);
CREATE INDEX IF NOT EXISTS idx_req_status ON req_schema.t_requirement(status);
CREATE INDEX IF NOT EXISTS idx_req_project ON req_schema.t_requirement(project_id);

-- ============================================================================
-- 5. 追溯管理模块表
-- ============================================================================

CREATE TABLE IF NOT EXISTS trace_schema.t_requirement_relation (
    id BIGSERIAL PRIMARY KEY,
    source_req_id BIGINT NOT NULL,
    target_req_id BIGINT NOT NULL,
    relation_type VARCHAR(50) NOT NULL, -- URS2PRS/PRS2SRS/SRS2DRS/HORIZONTAL
    project_id BIGINT,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS trace_schema.t_requirement_test_case (
    id BIGSERIAL PRIMARY KEY,
    requirement_id BIGINT NOT NULL,
    test_case_id BIGINT NOT NULL,
    trace_type VARCHAR(50) NOT NULL, -- DIRECT/INDIRECT
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rel_source ON trace_schema.t_requirement_relation(source_req_id);
CREATE INDEX IF NOT EXISTS idx_rel_target ON trace_schema.t_requirement_relation(target_req_id);

-- ============================================================================
-- 6. 变更管理模块表
-- ============================================================================

CREATE TABLE IF NOT EXISTS chg_schema.t_change_request (
    id BIGSERIAL PRIMARY KEY,
    change_no VARCHAR(50) NOT NULL UNIQUE,
    requirement_id BIGINT,
    change_type VARCHAR(50) NOT NULL, -- MAJOR/NORMAL/DOCUMENT/EMERGENCY
    title VARCHAR(500) NOT NULL,
    description TEXT,
    reason TEXT,
    urgency VARCHAR(20) DEFAULT 'MEDIUM',
    status VARCHAR(50) DEFAULT 'Draft',
    requester_id BIGINT,
    requester_name VARCHAR(100),
    reviewer_id BIGINT,
    planned_start_date DATE,
    planned_end_date DATE,
    actual_start_date DATE,
    actual_end_date DATE,
    risk_level VARCHAR(20),
    rollback_plan TEXT,
    affected_items TEXT,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chg_schema.t_impact_assessment (
    id BIGSERIAL PRIMARY KEY,
    change_request_id BIGINT NOT NULL,
    item_no VARCHAR(50),
    item_name VARCHAR(200),
    item_type VARCHAR(50),
    impact_level VARCHAR(20),
    impact_type VARCHAR(50),
    impact_description TEXT,
    suggested_action TEXT,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_change_status ON chg_schema.t_change_request(status);
CREATE INDEX IF NOT EXISTS idx_change_requirement ON chg_schema.t_change_request(requirement_id);

-- ============================================================================
-- 7. 合规管理模块表
-- ============================================================================

CREATE TABLE IF NOT EXISTS compliance_schema.t_baseline (
    id BIGSERIAL PRIMARY KEY,
    baseline_no VARCHAR(50) NOT NULL UNIQUE,
    baseline_name VARCHAR(200) NOT NULL,
    baseline_type VARCHAR(50) NOT NULL, -- URS/PRS/SRS/DRS
    project_id BIGINT,
    status VARCHAR(50) DEFAULT 'Draft',
    locked_by BIGINT,
    locked_at TIMESTAMP,
    snapshot_data JSONB,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS compliance_schema.t_compliance_check (
    id BIGSERIAL PRIMARY KEY,
    requirement_id BIGINT,
    requirement_no VARCHAR(50),
    regulation_type VARCHAR(50),
    check_item VARCHAR(200),
    check_result VARCHAR(50),
    status VARCHAR(50) DEFAULT 'PENDING',
    remarks TEXT,
    checked_by BIGINT,
    checker_name VARCHAR(100),
    checked_at TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS compliance_schema.t_dhf_evidence (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    evidence_type VARCHAR(50) NOT NULL,
    evidence_name VARCHAR(200) NOT NULL,
    file_path VARCHAR(500),
    file_size BIGINT,
    file_hash VARCHAR(100),
    uploaded_by BIGINT,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) DEFAULT 'UPLOADED',
    is_deleted BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS compliance_schema.t_soup_component (
    id BIGSERIAL PRIMARY KEY,
    component_name VARCHAR(200) NOT NULL,
    component_code VARCHAR(50) NOT NULL,
    version VARCHAR(50),
    supplier VARCHAR(200),
    supplier_country VARCHAR(50),
    software_category VARCHAR(50),
    compliance_standard VARCHAR(100),
    usage_scenario VARCHAR(500),
    integration_level VARCHAR(50),
    risk_level VARCHAR(20),
    certification_doc VARCHAR(500),
    license_type VARCHAR(50),
    license_expiry TIMESTAMP,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    security_disclosure TEXT,
    maintained_by VARCHAR(100),
    last_security_update TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_baseline_project ON compliance_schema.t_baseline(project_id);
CREATE INDEX IF NOT EXISTS idx_compliance_req ON compliance_schema.t_compliance_check(requirement_id);
CREATE INDEX IF NOT EXISTS idx_dhf_project ON compliance_schema.t_dhf_evidence(project_id);
CREATE INDEX IF NOT EXISTS idx_soup_status ON compliance_schema.t_soup_component(status);

-- ============================================================================
-- 8. 电子签名模块表
-- ============================================================================

CREATE TABLE IF NOT EXISTS esign_schema.t_signature_record (
    id BIGSERIAL PRIMARY KEY,
    signature_type VARCHAR(50) NOT NULL,
    intent VARCHAR(100) NOT NULL,
    signer_id BIGINT NOT NULL,
    signer_name VARCHAR(100) NOT NULL,
    signer_role VARCHAR(100),
    document_type VARCHAR(50),
    document_id BIGINT,
    document_no VARCHAR(50),
    signature_hash VARCHAR(200) NOT NULL,
    signature_method VARCHAR(50), -- OTP/PIN/CERT
    ip_address VARCHAR(50),
    device_info VARCHAR(200),
    is_valid BOOLEAN DEFAULT TRUE,
    signed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS esign_schema.t_signature_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    signature_password_hash VARCHAR(200),
    otp_secret VARCHAR(100),
    otp_enabled BOOLEAN DEFAULT FALSE,
    pin_hash VARCHAR(100),
    pin_enabled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sig_document ON esign_schema.t_signature_record(document_type, document_id);
CREATE INDEX IF NOT EXISTS idx_sig_signer ON esign_schema.t_signature_record(signer_id);

-- ============================================================================
-- 9. 风险管理模块表
-- ============================================================================

CREATE TABLE IF NOT EXISTS risk_schema.t_risk_assessment (
    id BIGSERIAL PRIMARY KEY,
    requirement_id BIGINT NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    hazard_level VARCHAR(50) NOT NULL,
    risk_score DECIMAL(10,2),
    hazard_source VARCHAR(500),
    hazard_situation VARCHAR(1000),
    harm VARCHAR(500),
    control_measure VARCHAR(2000),
    residual_risk VARCHAR(50),
    risk_status VARCHAR(50) DEFAULT 'OPEN',
    assessed_by BIGINT,
    assessed_at TIMESTAMP,
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_schema.t_risk_register (
    id BIGSERIAL PRIMARY KEY,
    risk_no VARCHAR(50) NOT NULL UNIQUE,
    risk_title VARCHAR(500) NOT NULL,
    category VARCHAR(50),
    severity VARCHAR(20),
    probability VARCHAR(20),
    detectability VARCHAR(20),
    risk_level VARCHAR(20),
    description TEXT,
    root_cause TEXT,
    control_measure TEXT,
    response_strategy VARCHAR(50),
    status VARCHAR(50) DEFAULT 'OPEN',
    owner_id BIGINT,
    owner_name VARCHAR(100),
    due_date DATE,
    closed_at TIMESTAMP,
    closure_note TEXT,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_schema.t_risk_matrix (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    project_no VARCHAR(50),
    matrix_type VARCHAR(50),
    severity VARCHAR(20),
    probability VARCHAR(20),
    detectability VARCHAR(20),
    rpn INTEGER,
    risk_level VARCHAR(20),
    risk_zone VARCHAR(50),
    description TEXT,
    mitigation_measure TEXT,
    residual_risk VARCHAR(50),
    residual_rpn INTEGER,
    assessed_at TIMESTAMP,
    assessed_by BIGINT,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_risk_req ON risk_schema.t_risk_assessment(requirement_id);
CREATE INDEX IF NOT EXISTS idx_risk_register_status ON risk_schema.t_risk_register(status);
CREATE INDEX IF NOT EXISTS idx_risk_matrix_project ON risk_schema.t_risk_matrix(project_id);

-- ============================================================================
-- 10. 项目管理模块表
-- ============================================================================

CREATE TABLE IF NOT EXISTS proj_schema.t_project (
    id BIGSERIAL PRIMARY KEY,
    project_no VARCHAR(50) NOT NULL UNIQUE,
    project_name VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(50) DEFAULT 'PLANNING',
    manager_id BIGINT,
    manager_name VARCHAR(100),
    start_date DATE,
    end_date DATE,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS proj_schema.t_ipd_gate (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    project_no VARCHAR(50),
    gate_no INTEGER NOT NULL,
    gate_name VARCHAR(100) NOT NULL,
    gate_type VARCHAR(50),
    status VARCHAR(50) DEFAULT 'PENDING',
    planned_date DATE,
    actual_date DATE,
    reviewer VARCHAR(100),
    decision VARCHAR(50),
    comment TEXT,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS proj_schema.t_project_member (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    project_no VARCHAR(50),
    user_id BIGINT NOT NULL,
    username VARCHAR(100),
    real_name VARCHAR(100),
    role VARCHAR(50),
    department VARCHAR(100),
    joined_at DATE,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS proj_schema.t_milestone (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    milestone_name VARCHAR(200) NOT NULL,
    milestone_type VARCHAR(50),
    planned_date DATE,
    actual_date DATE,
    status VARCHAR(50) DEFAULT 'PENDING',
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS proj_schema.t_gantt_task (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    task_name VARCHAR(200) NOT NULL,
    parent_task_id BIGINT,
    assignee_id BIGINT,
    assignee_name VARCHAR(100),
    start_date DATE,
    end_date DATE,
    progress INTEGER DEFAULT 0,
    status VARCHAR(50) DEFAULT 'PENDING',
    dependency JSONB,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_proj_status ON proj_schema.t_project(status);
CREATE INDEX IF NOT EXISTS idx_ipd_project ON proj_schema.t_ipd_gate(project_id);
CREATE INDEX IF NOT EXISTS idx_member_project ON proj_schema.t_project_member(project_id);
CREATE INDEX IF NOT EXISTS idx_gantt_project ON proj_schema.t_gantt_task(project_id);

-- ============================================================================
-- 11. 报表模块表
-- ============================================================================

CREATE TABLE IF NOT EXISTS report_schema.t_report (
    id BIGSERIAL PRIMARY KEY,
    report_type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    project_id BIGINT,
    file_path VARCHAR(500),
    generated_by BIGINT,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_report_type ON report_schema.t_report(report_type);
CREATE INDEX IF NOT EXISTS idx_report_project ON report_schema.t_report(project_id);

-- ============================================================================
-- 12. 系统管理模块表
-- ============================================================================

CREATE TABLE IF NOT EXISTS sys_schema.t_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(200) NOT NULL,
    real_name VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(50),
    department VARCHAR(100),
    role VARCHAR(50),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    signature_password_hash VARCHAR(200),
    last_login_at TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_schema.t_role (
    id BIGSERIAL PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL,
    role_code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    user_count INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_schema.t_dict_item (
    id BIGSERIAL PRIMARY KEY,
    dict_type VARCHAR(50) NOT NULL,
    item_code VARCHAR(50) NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    sort_order INTEGER DEFAULT 0,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_schema.t_system_config (
    id BIGSERIAL PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value VARCHAR(500),
    description VARCHAR(200),
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_username ON sys_schema.t_user(username);
CREATE INDEX IF NOT EXISTS idx_user_status ON sys_schema.t_user(status);
CREATE INDEX IF NOT EXISTS idx_dict_type ON sys_schema.t_dict_item(dict_type);
CREATE INDEX IF NOT EXISTS idx_config_key ON sys_schema.t_system_config(config_key);

-- ============================================================================
-- 13. 通知管理模块表
-- ============================================================================

CREATE TABLE IF NOT EXISTS not_schema.t_notification (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(100),
    title VARCHAR(200) NOT NULL,
    content TEXT,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'UNREAD',
    source_type VARCHAR(50),
    source_id BIGINT,
    read_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS not_schema.t_notification_template (
    id BIGSERIAL PRIMARY KEY,
    template_code VARCHAR(50) NOT NULL UNIQUE,
    template_name VARCHAR(100) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    subject_template VARCHAR(200),
    content_template TEXT,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS not_schema.t_email_queue (
    id BIGSERIAL PRIMARY KEY,
    to_address VARCHAR(200) NOT NULL,
    subject VARCHAR(200),
    content TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    retry_count INTEGER DEFAULT 0,
    sent_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS not_schema.t_notification_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    email_enabled BOOLEAN DEFAULT TRUE,
    system_enabled BOOLEAN DEFAULT TRUE,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notification_user ON not_schema.t_notification(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_status ON not_schema.t_notification(status);
CREATE INDEX IF NOT EXISTS idx_email_status ON not_schema.t_email_queue(status);

-- ============================================================================
-- 14. 审计日志表
-- ============================================================================

CREATE TABLE IF NOT EXISTS compliance_schema.t_audit_log (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,
    operation VARCHAR(50) NOT NULL,
    operator_id BIGINT,
    operator_name VARCHAR(100),
    old_value JSONB,
    new_value JSONB,
    hash_value VARCHAR(100),
    previous_hash VARCHAR(100),
    ip_address VARCHAR(50),
    user_agent VARCHAR(200),
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_entity ON compliance_schema.t_audit_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_operator ON compliance_schema.t_audit_log(operator_id);
CREATE INDEX IF NOT EXISTS idx_audit_time ON compliance_schema.t_audit_log(created_at);

-- ============================================================================
-- 15. 初始数据
-- ============================================================================

-- 插入默认管理员用户 (密码: admin123)
INSERT INTO sys_schema.t_user (username, password_hash, real_name, email, department, role, status)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '系统管理员', 'admin@medrms.com', 'IT', 'ADMIN', 'ACTIVE')
ON CONFLICT (username) DO NOTHING;

-- 插入字典数据
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order) VALUES
('req_level', 'URS', '用户需求规范', 1),
('req_level', 'PRS', '产品需求规范', 2),
('req_level', 'SRS', '软件需求规范', 3),
('req_level', 'DRS', '设计需求规范', 4),
('priority', 'MUST', '必须实现', 1),
('priority', 'SHOULD', '应该实现', 2),
('priority', 'COULD', '可以实现', 3),
('priority', 'WONT', '不会实现', 4),
('risk_level', 'HIGH', '高风险', 1),
('risk_level', 'MEDIUM', '中风险', 2),
('risk_level', 'LOW', '低风险', 3),
('change_type', 'MAJOR', '重大变更', 1),
('change_type', 'NORMAL', '普通变更', 2),
('change_type', 'DOCUMENT', '文档变更', 3),
('change_type', 'EMERGENCY', '紧急变更', 4),
('notification_type', 'REVIEW_REJECTED', '评审驳回', 1),
('notification_type', 'TRACE_BROKEN', '追溯断裂', 2),
('notification_type', 'RISK_ALERT', '风险预警', 3),
('notification_type', 'CHANGE_APPROVED', '变更批准', 4),
('notification_type', 'SYSTEM', '系统通知', 5)
ON CONFLICT DO NOTHING;

-- 插入示例项目
INSERT INTO proj_schema.t_project (project_no, project_name, description, status, manager_name, start_date)
VALUES ('PRJ-ECG3-001', '心电监护仪 v3.0', '新一代心电监护仪软件开发项目', 'IN_PROGRESS', '张工', '2026-01-01')
ON CONFLICT (project_no) DO NOTHING;

-- 插入示例需求
INSERT INTO req_schema.t_requirement (requirement_no, requirement_type, project_id, title, description, priority, status)
VALUES
('URS-ECG3-0001', 'URS', 1, '心电信号采集需求', '系统应能采集心电信号，采样率不低于500Hz', 'MUST', 'Approved'),
('PRS-ECG3-0001', 'PRS', 1, '心电信号处理需求', '系统应能实时处理心电信号，延迟不超过100ms', 'MUST', 'Approved'),
('SRS-ECG3-0001', 'SRS', 1, '心电信号滤波算法', '实现低通滤波算法，截止频率可配置', 'MUST', 'InReview'),
('DRS-ECG3-0001', 'DRS', 1, 'DSP芯片驱动设计', '设计DSP芯片驱动接口', 'MUST', 'Draft')
ON CONFLICT (requirement_no) DO NOTHING;

-- 插入追溯关系
INSERT INTO trace_schema.t_requirement_relation (source_req_id, target_req_id, relation_type, project_id)
SELECT s.id, t.id, 'URS2PRS', 1
FROM req_schema.t_requirement s, req_schema.t_requirement t
WHERE s.requirement_no = 'URS-ECG3-0001' AND t.requirement_no = 'PRS-ECG3-0001'
ON CONFLICT DO NOTHING;

-- 插入SOUP组件示例
INSERT INTO compliance_schema.t_soup_component (component_name, component_code, version, supplier, supplier_country, software_category, compliance_standard, integration_level, risk_level, status)
VALUES
('FreeRTOS', 'FREERTOS', '11.0.0', 'Amazon', '美国', 'RTOS', 'IEC 62304', 'B', 'HIGH', 'ACTIVE'),
('lwIP', 'LWIP', '2.1.3', 'The lwIP Project', '美国', 'TCP/IP Stack', 'IEC 62304', 'B', 'MEDIUM', 'ACTIVE')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 完成
-- ============================================================================
\echo 'Med-RMS 数据库初始化完成！'
