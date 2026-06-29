-- ============================================================================
-- Med-RMS 全流程测试数据
-- 数据库：med_rms_pms
-- 说明：包含项目、需求、追溯、变更、门控、任务、里程碑等完整流程数据
-- ============================================================================

-- 清理可能存在的旧数据（按外键顺序）
DELETE FROM not_schema.t_notification WHERE user_id > 0;
DELETE FROM trace_schema.t_requirement_test_case WHERE requirement_id > 0;
DELETE FROM trace_schema.t_requirement_relation WHERE project_id > 0;
DELETE FROM chg_schema.t_impact_assessment WHERE change_request_id > 0;
DELETE FROM chg_schema.t_change_request WHERE id > 0;
DELETE FROM req_schema.t_test_case WHERE requirement_id > 0;
DELETE FROM req_schema.t_requirement WHERE project_id > 0;
DELETE FROM risk_schema.t_risk_assessment WHERE requirement_id > 0;
DELETE FROM risk_schema.t_risk_register WHERE id > 0;
DELETE FROM compliance_schema.t_compliance_check WHERE requirement_id > 0;
DELETE FROM compliance_schema.t_baseline WHERE project_id > 0;
DELETE FROM proj_schema.t_milestone WHERE project_id > 0;
DELETE FROM proj_schema.t_task WHERE project_id > 0;
DELETE FROM proj_schema.t_ipd_gate WHERE project_id > 0;
DELETE FROM proj_schema.t_project_member WHERE project_id > 0;
DELETE FROM proj_schema.t_project WHERE id > 0;

-- ============================================================================
-- 1. 项目数据 (proj_schema.t_project)
-- ============================================================================
INSERT INTO proj_schema.t_project (id, project_no, project_name, description, status, manager_id, manager_name, start_date, end_date)
VALUES
(1, 'PRJ-ECG3-001', '心电监护仪 v3.0', '新一代心电监护仪软件开发项目，基于ARM Cortex-M4平台，实现心电、血氧、血压多参数监护', 'IN_PROGRESS', 1, '张工', '2026-01-01', '2026-12-31'),
(2, 'PRJ-SPO2-002', '脉搏血氧仪开发', '便携式脉搏血氧仪软件开发', 'PLANNING', 2, '李工', '2026-03-01', '2026-09-30'),
(3, 'PRJ-NIBP-003', '无创血压监护', '高端监护仪无创血压模块开发', 'COMPLETED', 1, '张工', '2025-06-01', '2025-12-31')
ON CONFLICT (project_no) DO UPDATE SET
    project_name = EXCLUDED.project_name,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    manager_name = EXCLUDED.manager_name;

-- ============================================================================
-- 2. 项目成员 (proj_schema.t_project_member)
-- ============================================================================
INSERT INTO proj_schema.t_project_member (project_id, project_no, user_id, username, real_name, role, department, joined_at, status)
VALUES
(1, 'PRJ-ECG3-001', 1, 'zhang', '张工', 'PROJECT_MANAGER', '研发部', '2026-01-01', 'ACTIVE'),
(1, 'PRJ-ECG3-001', 2, 'li', '李工', 'REQUIREMENT_ENGINEER', '需求部', '2026-01-01', 'ACTIVE'),
(1, 'PRJ-ECG3-001', 3, 'wang', '王工', 'DEVELOPER', '研发部', '2026-01-15', 'ACTIVE'),
(1, 'PRJ-ECG3-001', 4, 'zhao', '赵工', 'TESTER', '测试部', '2026-02-01', 'ACTIVE'),
(1, 'PRJ-ECG3-001', 5, 'sun', '孙工', 'QUALITY', '质量部', '2026-02-15', 'ACTIVE'),
(2, 'PRJ-SPO2-002', 2, 'li', '李工', 'PROJECT_MANAGER', '需求部', '2026-03-01', 'ACTIVE'),
(2, 'PRJ-SPO2-002', 6, 'zhou', '周工', 'DEVELOPER', '研发部', '2026-03-01', 'ACTIVE')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 3. IPD门控 (proj_schema.t_ipd_gate)
-- ============================================================================
INSERT INTO proj_schema.t_ipd_gate (project_id, project_no, gate_no, gate_name, gate_type, status, planned_date, actual_date, reviewer, decision)
VALUES
(1, 'PRJ-ECG3-001', 1, '概念阶段门', 'DCP1', 'COMPLETED', '2026-02-15', '2026-02-14', '张工', 'PASS'),
(1, 'PRJ-ECG3-001', 2, '计划阶段门', 'DCP2', 'COMPLETED', '2026-04-15', '2026-04-16', '张工', 'PASS'),
(1, 'PRJ-ECG3-001', 3, '设计阶段门', 'DCP3', 'IN_PROGRESS', '2026-07-15', NULL, '张工', NULL),
(1, 'PRJ-ECG3-001', 4, '开发阶段门', 'DCP4', 'PENDING', '2026-10-15', NULL, NULL, NULL),
(1, 'PRJ-ECG3-001', 5, '验证阶段门', 'DCP5', 'PENDING', '2026-12-15', NULL, NULL, NULL)
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 4. 里程碑 (proj_schema.t_milestone) - 注意列名：name, gate_type
-- ============================================================================
INSERT INTO proj_schema.t_milestone (project_id, name, gate_type, planned_date, actual_date, status)
VALUES
(1, '概念阶段里程碑', 'DCP1', '2026-02-15', '2026-02-14', 'COMPLETED'),
(1, '计划阶段里程碑', 'DCP2', '2026-04-15', '2026-04-16', 'COMPLETED'),
(1, '设计阶段里程碑', 'DCP3', '2026-07-15', NULL, 'IN_PROGRESS'),
(1, '开发阶段里程碑', 'DCP4', '2026-10-15', NULL, 'PLANNED'),
(1, '验证阶段里程碑', 'DCP5', '2026-12-15', NULL, 'PLANNED')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 5. 任务 (proj_schema.t_task) - 注意列名：title, estimated_hours, actual_hours
-- ============================================================================
INSERT INTO proj_schema.t_task (project_id, task_no, title, description, assignee_id, assignee_name, start_date, end_date, estimated_hours, actual_hours, status, priority, milestone_id)
VALUES
-- DCP1里程碑任务
(1, 'TASK-000001', '市场调研与需求分析', '完成心电监护仪市场需求调研和初步需求分析', 2, '李工', '2026-01-01', '2026-01-31', 80, 85, 'DONE', 'HIGH', 1),
(1, 'TASK-000002', '概念设计评审', '完成产品概念设计方案并通过评审', 1, '张工', '2026-02-01', '2026-02-14', 40, 38, 'DONE', 'HIGH', 1),

-- DCP2里程碑任务
(1, 'TASK-000003', '需求详细分析', '完成URS/PRS文档编写和评审', 2, '李工', '2026-03-01', '2026-03-31', 120, 115, 'DONE', 'HIGH', 2),
(1, 'TASK-000004', '系统架构设计', '完成系统架构设计和评审', 3, '王工', '2026-03-15', '2026-04-15', 100, 110, 'DONE', 'HIGH', 2),

-- DCP3里程碑任务（进行中）
(1, 'TASK-000005', '心电信号采集模块开发', '实现心电信号采集和处理模块', 3, '王工', '2026-05-01', '2026-06-30', 160, 80, 'IN_PROGRESS', 'HIGH', 3),
(1, 'TASK-000006', 'UI界面设计与开发', '完成监护界面UI设计和开发', 3, '王工', '2026-05-15', '2026-07-15', 140, 50, 'IN_PROGRESS', 'MEDIUM', 3),
(1, 'TASK-000007', '报警算法实现', '实现心电异常报警算法', 3, '王工', '2026-06-01', '2026-07-31', 120, 30, 'TODO', 'HIGH', 3),

-- DCP4里程碑任务（待做）
(1, 'TASK-000008', '系统集成测试', '完成各模块集成测试', 4, '赵工', '2026-08-01', '2026-09-30', 200, 0, 'TODO', 'HIGH', 4),
(1, 'TASK-000009', '临床验证', '完成临床验证和EMC测试', 4, '赵工', '2026-10-01', '2026-11-15', 180, 0, 'TODO', 'HIGH', 4),

-- DCP5里程碑任务
(1, 'TASK-000010', '注册申报', '完成产品注册和型式检验', 5, '孙工', '2026-11-15', '2026-12-31', 160, 0, 'TODO', 'HIGH', 5)
ON CONFLICT (task_no) DO UPDATE SET title = EXCLUDED.title;

-- ============================================================================
-- 6. 需求数据 (req_schema.t_requirement)
-- ============================================================================
INSERT INTO req_schema.t_requirement (requirement_no, requirement_type, project_id, title, description, priority, risk_level, safety_class, status, created_by)
VALUES
-- URS (用户需求)
('URS-ECG3-0001', 'URS', 1, '心电信号采集功能', '系统应能采集心电信号，采样率不低于500Hz，分辨率不低于12位', 'MUST', 'HIGH', 'B', 'Approved', 1),
('URS-ECG3-0002', 'URS', 1, '心电波形显示功能', '系统应能实时显示心电波形，刷新率不低于60fps，波形清晰无失真', 'MUST', 'HIGH', 'B', 'Approved', 1),
('URS-ECG3-0003', 'URS', 1, '心率报警功能', '系统应能检测心率异常并发出报警，报警延迟不超过1秒', 'MUST', 'HIGH', 'B', 'Approved', 1),
('URS-ECG3-0004', 'URS', 1, '血氧监测功能', '系统应能监测SpO2，范围80%-100%，精度±2%', 'SHOULD', 'MEDIUM', 'B', 'Approved', 1),
('URS-ECG3-0005', 'URS', 1, '数据存储功能', '系统应能存储至少72小时监测数据，支持导出和回顾', 'SHOULD', 'LOW', 'C', 'Approved', 1),

-- PRS (产品需求)
('PRS-ECG3-0001', 'PRS', 1, '心电信号处理算法', '实现数字滤波算法，截止频率可配置，低通滤波器衰减≥40dB', 'MUST', 'HIGH', 'B', 'Approved', 2),
('PRS-ECG3-0002', 'PRS', 1, '心率计算算法', '实现实时心率计算，更新间隔1秒，精度±1bpm', 'MUST', 'HIGH', 'B', 'Approved', 2),
('PRS-ECG3-0003', 'PRS', 1, 'ST段分析功能', '实现ST段抬高/压低分析，检测时间不超过5秒', 'SHOULD', 'MEDIUM', 'B', 'InReview', 2),

-- SRS (软件需求)
('SRS-ECG3-0001', 'SRS', 1, '心电采集驱动设计', '设计DSP芯片驱动接口，支持多导联心电采集', 'MUST', 'HIGH', 'B', 'Approved', 3),
('SRS-ECG3-0002', 'SRS', 1, '实时操作系统移植', '完成FreeRTOS在目标平台的移植和配置', 'MUST', 'MEDIUM', 'B', 'Approved', 3),
('SRS-ECG3-0003', 'SRS', 1, '通信协议栈实现', '实现HL7协议栈，支持与HIS系统对接', 'COULD', 'LOW', 'C', 'Draft', 3),

-- DRS (设计需求)
('DRS-ECG3-0001', 'DRS', 1, 'PCB设计规范', '4层PCB设计，满足EMC要求', 'MUST', 'HIGH', 'B', 'Draft', 3),
('DRS-ECG3-0002', 'DRS', 1, '结构设计规范', '外壳防护等级IPX1', 'SHOULD', 'MEDIUM', 'B', 'Draft', 3)
ON CONFLICT (requirement_no) DO UPDATE SET title = EXCLUDED.title;

-- ============================================================================
-- 7. 追溯关系 (trace_schema.t_requirement_relation)
-- ============================================================================
-- URS -> PRS 追溯
INSERT INTO trace_schema.t_requirement_relation (source_req_id, target_req_id, relation_type, project_id)
SELECT s.id, t.id, 'URS2PRS', 1
FROM req_schema.t_requirement s, req_schema.t_requirement t
WHERE s.requirement_no = 'URS-ECG3-0001' AND t.requirement_no = 'PRS-ECG3-0001'
ON CONFLICT DO NOTHING;

INSERT INTO trace_schema.t_requirement_relation (source_req_id, target_req_id, relation_type, project_id)
SELECT s.id, t.id, 'URS2PRS', 1
FROM req_schema.t_requirement s, req_schema.t_requirement t
WHERE s.requirement_no = 'URS-ECG3-0002' AND t.requirement_no = 'PRS-ECG3-0001'
ON CONFLICT DO NOTHING;

INSERT INTO trace_schema.t_requirement_relation (source_req_id, target_req_id, relation_type, project_id)
SELECT s.id, t.id, 'URS2PRS', 1
FROM req_schema.t_requirement s, req_schema.t_requirement t
WHERE s.requirement_no = 'URS-ECG3-0003' AND t.requirement_no = 'PRS-ECG3-0002'
ON CONFLICT DO NOTHING;

-- PRS -> SRS 追溯
INSERT INTO trace_schema.t_requirement_relation (source_req_id, target_req_id, relation_type, project_id)
SELECT s.id, t.id, 'PRS2SRS', 1
FROM req_schema.t_requirement s, req_schema.t_requirement t
WHERE s.requirement_no = 'PRS-ECG3-0001' AND t.requirement_no = 'SRS-ECG3-0001'
ON CONFLICT DO NOTHING;

INSERT INTO trace_schema.t_requirement_relation (source_req_id, target_req_id, relation_type, project_id)
SELECT s.id, t.id, 'PRS2SRS', 1
FROM req_schema.t_requirement s, req_schema.t_requirement t
WHERE s.requirement_no = 'PRS-ECG3-0001' AND t.requirement_no = 'SRS-ECG3-0002'
ON CONFLICT DO NOTHING;

INSERT INTO trace_schema.t_requirement_relation (source_req_id, target_req_id, relation_type, project_id)
SELECT s.id, t.id, 'PRS2SRS', 1
FROM req_schema.t_requirement s, req_schema.t_requirement t
WHERE s.requirement_no = 'PRS-ECG3-0002' AND t.requirement_no = 'SRS-ECG3-0001'
ON CONFLICT DO NOTHING;

-- SRS -> DRS 追溯
INSERT INTO trace_schema.t_requirement_relation (source_req_id, target_req_id, relation_type, project_id)
SELECT s.id, t.id, 'SRS2DRS', 1
FROM req_schema.t_requirement s, req_schema.t_requirement t
WHERE s.requirement_no = 'SRS-ECG3-0001' AND t.requirement_no = 'DRS-ECG3-0001'
ON CONFLICT DO NOTHING;

INSERT INTO trace_schema.t_requirement_relation (source_req_id, target_req_id, relation_type, project_id)
SELECT s.id, t.id, 'SRS2DRS', 1
FROM req_schema.t_requirement s, req_schema.t_requirement t
WHERE s.requirement_no = 'SRS-ECG3-0002' AND t.requirement_no = 'DRS-ECG3-0001'
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 8. 测试用例 (req_schema.t_test_case)
-- ============================================================================
INSERT INTO req_schema.t_test_case (test_case_no, test_case_name, test_type, test_method, requirement_id, requirement_no, status)
SELECT 'TC-ECG3-001', '心电采样率测试', '功能测试', '黑盒测试', s.id, s.requirement_no, 'Approved'
FROM req_schema.t_requirement s WHERE s.requirement_no = 'URS-ECG3-0001'
ON CONFLICT (test_case_no) DO NOTHING;

INSERT INTO req_schema.t_test_case (test_case_no, test_case_name, test_type, test_method, requirement_id, requirement_no, status)
SELECT 'TC-ECG3-002', '心电波形显示测试', '功能测试', '黑盒测试', s.id, s.requirement_no, 'Approved'
FROM req_schema.t_requirement s WHERE s.requirement_no = 'URS-ECG3-0002'
ON CONFLICT (test_case_no) DO NOTHING;

INSERT INTO req_schema.t_test_case (test_case_no, test_case_name, test_type, test_method, requirement_id, requirement_no, status)
SELECT 'TC-ECG3-003', '心率报警响应时间测试', '性能测试', '压力测试', s.id, s.requirement_no, 'Approved'
FROM req_schema.t_requirement s WHERE s.requirement_no = 'URS-ECG3-0003'
ON CONFLICT (test_case_no) DO NOTHING;

INSERT INTO req_schema.t_test_case (test_case_no, test_case_name, test_type, test_method, requirement_id, requirement_no, status)
SELECT 'TC-ECG3-004', '心电信号处理算法精度测试', '算法验证', '白盒测试', s.id, s.requirement_no, 'InReview'
FROM req_schema.t_requirement s WHERE s.requirement_no = 'PRS-ECG3-0001'
ON CONFLICT (test_case_no) DO NOTHING;

-- ============================================================================
-- 9. 需求-测试用例追溯 (trace_schema.t_requirement_test_case)
-- ============================================================================
INSERT INTO trace_schema.t_requirement_test_case (requirement_id, test_case_id, trace_type)
SELECT r.id, t.id, 'DIRECT'
FROM req_schema.t_requirement r, req_schema.t_test_case t
WHERE r.requirement_no = 'URS-ECG3-0001' AND t.test_case_no = 'TC-ECG3-001'
ON CONFLICT DO NOTHING;

INSERT INTO trace_schema.t_requirement_test_case (requirement_id, test_case_id, trace_type)
SELECT r.id, t.id, 'DIRECT'
FROM req_schema.t_requirement r, req_schema.t_test_case t
WHERE r.requirement_no = 'URS-ECG3-0002' AND t.test_case_no = 'TC-ECG3-002'
ON CONFLICT DO NOTHING;

INSERT INTO trace_schema.t_requirement_test_case (requirement_id, test_case_id, trace_type)
SELECT r.id, t.id, 'DIRECT'
FROM req_schema.t_requirement r, req_schema.t_test_case t
WHERE r.requirement_no = 'URS-ECG3-0003' AND t.test_case_no = 'TC-ECG3-003'
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 10. 变更请求 (chg_schema.t_change_request)
-- ============================================================================
INSERT INTO chg_schema.t_change_request (change_no, requirement_id, change_type, title, description, reason, urgency, status, requester_id, requester_name, planned_start_date, planned_end_date)
VALUES
('CR-ECG3-001', 1, 'MAJOR', '心电采样率提升需求变更', '将心电采样率从500Hz提升至1000Hz，以满足更精确的临床诊断需求', '临床需求反馈', 'HIGH', 'Approved', 1, '张工', '2026-04-01', '2026-04-30'),
('CR-ECG3-002', 3, 'NORMAL', '报警阈值优化变更', '根据临床反馈调整心率报警阈值范围', '临床优化建议', 'MEDIUM', 'InReview', 2, '李工', '2026-05-01', '2026-05-15')
ON CONFLICT (change_no) DO UPDATE SET title = EXCLUDED.title;

-- ============================================================================
-- 11. 风险评估 (risk_schema.t_risk_assessment)
-- ============================================================================
INSERT INTO risk_schema.t_risk_assessment (requirement_id, risk_level, hazard_level, risk_score, hazard_source, hazard_situation, harm, control_measure, residual_risk, risk_status, assessed_by, assessed_at)
VALUES
(1, 'HIGH', 'CRITICAL', 85.0, '采样率不足导致心电信号失真', '心电信号采集模块采样率低于500Hz', '可能导致心律失常漏检，危及患者生命', '增加采样率至1000Hz，增加信号预处理滤波', 'ACCEPTABLE', 'CLOSED', 1, '2026-02-15'),
(3, 'HIGH', 'CRITICAL', 90.0, '报警延迟过长', '报警算法处理延迟超过1秒', '可能延误抢救时机，危及患者生命', '优化报警算法，预处理心跳检测', 'ALARP', 'MONITORING', 1, '2026-02-20'),
(6, 'MEDIUM', 'MAJOR', 60.0, '滤波算法性能不足', '低通滤波器计算量大，CPU占用高', '可能导致系统响应缓慢', '优化算法实现，使用查表法', 'ACCEPTABLE', 'OPEN', 2, '2026-03-10')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 12. 风险登记册 (risk_schema.t_risk_register)
-- ============================================================================
INSERT INTO risk_schema.t_risk_register (risk_no, risk_title, category, severity, probability, detectability, risk_level, description, control_measure, response_strategy, status, owner_id, owner_name, due_date)
VALUES
('RISK-001', '心电信号采集精度不足', '技术风险', 'CRITICAL', 'MEDIUM', 'LOW', 'HIGH', '当前硬件设计可能无法满足500Hz采样率要求', '选用高性能ADC芯片，增加信号调理电路', 'MITIGATION', 'OPEN', 3, '王工', '2026-04-30'),
('RISK-002', '临床验证周期不足', '进度风险', 'MAJOR', 'HIGH', 'MEDIUM', 'MEDIUM', '临床验证时间可能不足以发现所有问题', '提前启动临床验证，增加测试样本量', 'MITIGATION', 'MONITORING', 4, '赵工', '2026-10-31'),
('RISK-003', '注册检验不通过', '合规风险', 'CRITICAL', 'LOW', 'HIGH', 'HIGH', 'EMC测试和安规测试可能不符合要求', '提前进行预测试，邀请专家指导', 'CONTINGENCY', 'OPEN', 5, '孙工', '2026-11-30')
ON CONFLICT (risk_no) DO UPDATE SET risk_title = EXCLUDED.risk_title;

-- ============================================================================
-- 13. 合规检查 (compliance_schema.t_compliance_check)
-- ============================================================================
INSERT INTO compliance_schema.t_compliance_check (requirement_id, requirement_no, regulation_type, check_item, check_result, status, checked_by, checker_name, checked_at)
VALUES
(1, 'URS-ECG3-0001', 'IEC 60601-2-25', '心电图机安全要求', 'PASS', 'APPROVED', 1, '张工', '2026-03-01'),
(1, 'URS-ECG3-0001', 'IEC 60601-2-25', '心电图机性能要求', 'PASS', 'APPROVED', 1, '张工', '2026-03-01'),
(2, 'URS-ECG3-0002', 'IEC 60601-2-27', '心电监护设备EMC要求', 'PENDING', 'PENDING', NULL, NULL, NULL),
(6, 'PRS-ECG3-0001', 'IEC 62304', '软件生命周期要求', 'PASS', 'APPROVED', 2, '李工', '2026-03-15')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 14. 基线 (compliance_schema.t_baseline)
-- ============================================================================
INSERT INTO compliance_schema.t_baseline (baseline_no, baseline_name, baseline_type, project_id, status, locked_by, locked_at, snapshot_data)
VALUES
('BL-ECG3-URS-001', '心电监护仪URS基线', 'URS', 1, 'Locked', 1, '2026-02-28', '{"requirements": ["URS-ECG3-0001", "URS-ECG3-0002", "URS-ECG3-0003"]}'),
('BL-ECG3-PRS-001', '心电监护仪PRS基线', 'PRS', 1, 'Locked', 1, '2026-03-31', '{"requirements": ["PRS-ECG3-0001", "PRS-ECG3-0002"]}')
ON CONFLICT (baseline_no) DO UPDATE SET status = EXCLUDED.status;

-- ============================================================================
-- 15. 通知数据 (not_schema.t_notification)
-- ============================================================================
INSERT INTO not_schema.t_notification (user_id, user_name, title, content, type, status, source_type, source_id)
VALUES
(1, '张工', '需求评审通过', 'URS-ECG3-0001 心电信号采集功能 已通过评审', 'REVIEW_APPROVED', 'UNREAD', 'requirement', 1),
(1, '张工', '追溯断裂告警', 'URS-ECG3-0004 缺少测试用例追溯关系', 'TRACE_BROKEN', 'UNREAD', 'requirement', 4),
(1, '张工', '风险预警', 'RISK-001 心电信号采集精度不足风险需要关注', 'RISK_ALERT', 'READ', 'risk', 1),
(2, '李工', '变更申请审批', 'CR-ECG3-001 心电采样率提升需求变更 待审批', 'CHANGE_APPROVED', 'UNREAD', 'change', 1),
(1, '张工', '门控评审通知', 'DCP3 设计阶段门控将于2026-07-15进行评审', 'SYSTEM', 'UNREAD', 'gate', 3)
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 16. 签名记录 (esign_schema.t_signature_record)
-- ============================================================================
INSERT INTO esign_schema.t_signature_record (signature_type, intent, signer_id, signer_name, signer_role, document_type, document_id, document_no, signature_hash, signature_method, is_valid, signed_at)
VALUES
('APPROVAL', '需求评审批准', 1, '张工', '项目经理', 'requirement', 1, 'URS-ECG3-0001', 'a1b2c3d4e5f6', 'OTP', true, '2026-02-20 10:30:00'),
('APPROVAL', '需求评审批准', 2, '李工', '需求工程师', 'requirement', 1, 'URS-ECG3-0001', 'b2c3d4e5f6g7', 'OTP', true, '2026-02-20 14:20:00'),
('APPROVAL', '基线锁定批准', 1, '张工', '项目经理', 'baseline', 1, 'BL-ECG3-URS-001', 'c3d4e5f6g7h8', 'CERT', true, '2026-02-28 16:00:00')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 完成
-- ============================================================================
\echo '=========================================='
\echo 'Med-RMS 全流程测试数据插入完成！'
\echo '=========================================='
\echo '项目数: 3'
\echo '需求数: 13'
\echo '任务数: 10'
\echo '里程碑数: 5'
\echo '门控数: 5'
\echo '追溯关系数: 8'
\echo '=========================================='