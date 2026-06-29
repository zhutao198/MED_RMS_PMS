-- ============================================================================
-- Med-RMS 测试数据修复和补充
-- ============================================================================

-- 1. 创建需求收集池表
CREATE TABLE IF NOT EXISTS req_schema.t_requirement_pool (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(50) NOT NULL,
    source_no VARCHAR(100),
    title VARCHAR(200),
    raw_description TEXT,
    parsed_description TEXT,
    priority VARCHAR(20),
    status VARCHAR(20) DEFAULT 'PENDING',
    project_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    converted_to_id BIGINT,
    conversion_notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_pool_source ON req_schema.t_requirement_pool(source);
CREATE INDEX IF NOT EXISTS idx_pool_status ON req_schema.t_requirement_pool(status);

-- 2. 插入需求池测试数据
INSERT INTO req_schema.t_requirement_pool (source, source_no, title, raw_description, priority, status, project_id, created_by)
VALUES
('CUSTOMER', 'CUST-001', '心电监护远程报警', '医生希望能在手机端接收患者的心电异常报警通知', 'MUST', 'PENDING', 1, 1),
('CUSTOMER', 'CUST-002', '历史数据回顾', '希望能够查看最近30天的监测数据并进行对比分析', 'SHOULD', 'PENDING', 1, 1),
('MARKET', 'MKT-003', '儿童模式', '儿童医院反馈需要专门的儿童心电监护模式', 'SHOULD', 'PARSED', 1, 2),
('REGULATION', 'IEC-60601-2-25', '心电图机EMC要求', '符合IEC 60601-2-25:2015心电图机安全要求', 'MUST', 'CONVERTED', 1, 1),
('INTERNAL', 'INT-004', '功耗优化', '硬件团队反馈当前功耗偏高，需要优化以延长电池续航', 'COULD', 'PENDING', 1, 3),
('COMPETITOR', 'COMP-005', '趋势分析', '竞品有趋势分析功能，建议我们也要支持', 'COULD', 'REJECTED', 1, 2),
('REGULATION', 'GB-9706.1', '医用电气设备安全通用要求', '符合GB 9706.1-202X医用电气设备安全通用要求', 'MUST', 'PENDING', 1, 1)
ON CONFLICT DO NOTHING;

-- 3. 修复里程碑数据（需要 milestone_no）
DELETE FROM proj_schema.t_milestone WHERE project_id = 1;

INSERT INTO proj_schema.t_milestone (project_id, milestone_no, name, gate_type, planned_date, actual_date, status)
VALUES
(1, 'MS-ECG3-001', '概念阶段里程碑', 'DCP1', '2026-02-15', '2026-02-14', 'COMPLETED'),
(1, 'MS-ECG3-002', '计划阶段里程碑', 'DCP2', '2026-04-15', '2026-04-16', 'COMPLETED'),
(1, 'MS-ECG3-003', '设计阶段里程碑', 'DCP3', '2026-07-15', NULL, 'IN_PROGRESS'),
(1, 'MS-ECG3-004', '开发阶段里程碑', 'DCP4', '2026-10-15', NULL, 'PLANNED'),
(1, 'MS-ECG3-005', '验证阶段里程碑', 'DCP5', '2026-12-15', NULL, 'PLANNED')
ON CONFLICT (milestone_no) DO NOTHING;

-- 4. 修复任务数据（添加 milestone_id 列，里程碑ID为2-6）
DELETE FROM proj_schema.t_task WHERE project_id = 1;

INSERT INTO proj_schema.t_task (project_id, task_no, title, description, assignee_id, assignee_name, start_date, end_date, estimated_hours, actual_hours, status, priority, requirement_id, milestone_id)
VALUES
(1, 'TASK-000001', '市场调研与需求分析', '完成心电监护仪市场需求调研和初步需求分析', 2, '李工', '2026-01-01', '2026-01-31', 80, 85, 'DONE', 'HIGH', NULL, 2),
(1, 'TASK-000002', '概念设计评审', '完成产品概念设计方案并通过评审', 1, '张工', '2026-02-01', '2026-02-14', 40, 38, 'DONE', 'HIGH', NULL, 2),
(1, 'TASK-000003', '需求详细分析', '完成URS/PRS文档编写和评审', 2, '李工', '2026-03-01', '2026-03-31', 120, 115, 'DONE', 'HIGH', NULL, 3),
(1, 'TASK-000004', '系统架构设计', '完成系统架构设计和评审', 3, '王工', '2026-03-15', '2026-04-15', 100, 110, 'DONE', 'HIGH', NULL, 3),
(1, 'TASK-000005', '心电信号采集模块开发', '实现心电信号采集和处理模块', 3, '王工', '2026-05-01', '2026-06-30', 160, 80, 'IN_PROGRESS', 'HIGH', NULL, 4),
(1, 'TASK-000006', 'UI界面设计与开发', '完成监护界面UI设计和开发', 3, '王工', '2026-05-15', '2026-07-15', 140, 50, 'IN_PROGRESS', 'MEDIUM', NULL, 4),
(1, 'TASK-000007', '报警算法实现', '实现心电异常报警算法', 3, '王工', '2026-06-01', '2026-07-31', 120, 30, 'TODO', 'HIGH', NULL, 4),
(1, 'TASK-000008', '系统集成测试', '完成各模块集成测试', 4, '赵工', '2026-08-01', '2026-09-30', 200, 0, 'TODO', 'HIGH', NULL, 5),
(1, 'TASK-000009', '临床验证', '完成临床验证和EMC测试', 4, '赵工', '2026-10-01', '2026-11-15', 180, 0, 'TODO', 'HIGH', NULL, 6),
(1, 'TASK-000010', '注册申报', '完成产品注册和型式检验', 5, '孙工', '2026-11-15', '2026-12-31', 160, 0, 'TODO', 'HIGH', NULL, 6)
ON CONFLICT (task_no) DO UPDATE SET title = EXCLUDED.title;

\echo '测试数据修复完成！'