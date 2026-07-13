-- ============================================================================
-- 数据库清理脚本：删除所有测试数据，保留种子/引用数据
-- 生成日期: 2026-07-13
-- 用法: psql -U postgres -d med_rms_pms -f tools/cleanup_database.sql
-- ============================================================================

BEGIN;

-- ============================================================================
-- Step 1: 禁用所有触发器（避免 FK 影响删除顺序）
-- ============================================================================
SET session_replication_role = 'replica';

-- ============================================================================
-- Step 2: 清空业务数据表（按依赖顺序 DELETE，无依赖的表 TRUNCATE）
-- ============================================================================

-- ---------- 追溯管理 ----------
TRUNCATE trace_schema.t_requirement_relation CASCADE;
TRUNCATE trace_schema.t_trace_link CASCADE;
TRUNCATE trace_schema.t_trace_gap_ignored CASCADE;

-- ---------- 变更管理 ----------
DELETE FROM chg_schema.t_change_approval;
DELETE FROM chg_schema.t_change_timeline;
DELETE FROM chg_schema.t_change_execution;
DELETE FROM chg_schema.t_impact_assessment;
DELETE FROM chg_schema.t_change_attachment;
TRUNCATE chg_schema.t_change_request CASCADE;

-- ---------- 需求管理 ----------
TRUNCATE req_schema.t_requirement_ancestor CASCADE;
TRUNCATE req_schema.t_requirement_version CASCADE;
DELETE FROM req_schema.t_user_requirement;
DELETE FROM req_schema.t_product_requirement;
DELETE FROM req_schema.t_system_requirement;
DELETE FROM req_schema.t_design_requirement;
DELETE FROM req_schema.t_review;
TRUNCATE req_schema.t_requirement CASCADE;
TRUNCATE req_schema.t_requirement_pool CASCADE;
TRUNCATE req_schema.t_test_case CASCADE;

-- ---------- 合规管理 ----------
TRUNCATE compliance_schema.t_audit_log CASCADE;
TRUNCATE compliance_schema.t_baseline CASCADE;
DELETE FROM compliance_schema.baseline_item;
TRUNCATE compliance_schema.t_iec62304_checklist CASCADE;
TRUNCATE compliance_schema.t_compliance_check CASCADE;
TRUNCATE compliance_schema.t_dhf_evidence CASCADE;
TRUNCATE compliance_schema.t_soup_component CASCADE;
TRUNCATE compliance_schema.t_pr_correction CASCADE;
TRUNCATE compliance_schema.t_problem_report CASCADE;
TRUNCATE compliance_schema.t_safety_classification CASCADE;
TRUNCATE compliance_schema.t_report_config CASCADE;
TRUNCATE compliance_schema.regulatory_mapping CASCADE;

-- ---------- 电子签名 ----------
TRUNCATE esign_schema.t_signature_intent CASCADE;
TRUNCATE esign_schema.t_signature_record CASCADE;
TRUNCATE esign_schema.t_signature_settings CASCADE;
TRUNCATE esign_schema.outbox CASCADE;
TRUNCATE esign_schema.jwt_blacklist CASCADE;

-- ---------- 风险管理 ----------
TRUNCATE risk_schema.t_risk_register CASCADE;
TRUNCATE risk_schema.t_risk_assessment CASCADE;
TRUNCATE risk_schema.t_risk_matrix CASCADE;
DELETE FROM risk_schema.risk_item;

-- ---------- 项目管理 ----------
TRUNCATE proj_schema.t_project CASCADE;
TRUNCATE proj_schema.t_milestone CASCADE;
TRUNCATE proj_schema.t_task CASCADE;
TRUNCATE proj_schema.t_gantt_task CASCADE;
TRUNCATE proj_schema.t_ipd_gate CASCADE;
TRUNCATE proj_schema.t_project_deliverable CASCADE;
TRUNCATE proj_schema.t_project_activity CASCADE;
DELETE FROM proj_schema.project_member;
DELETE FROM proj_schema.t_project_member;
TRUNCATE proj_schema.t_compliance_template CASCADE;

-- ---------- 任务依赖 + 工时 ----------
TRUNCATE prj_schema.t_task_predecessor CASCADE;
TRUNCATE prj_schema.t_worklog CASCADE;

-- ---------- 通知 ----------
TRUNCATE not_schema.t_notification CASCADE;
TRUNCATE not_schema.t_email_queue CASCADE;
TRUNCATE not_schema.t_im_queue CASCADE;
TRUNCATE not_schema.t_notification_channel CASCADE;
TRUNCATE not_schema.t_notification_settings CASCADE;
TRUNCATE not_schema.t_notification_template CASCADE;

-- ---------- 报表 ----------
TRUNCATE report_schema.statistics_snapshot CASCADE;
TRUNCATE report_schema.t_report CASCADE;
TRUNCATE report_schema.dashboard_config CASCADE;
TRUNCATE report_schema.report_template CASCADE;

-- ---------- 其他 ----------
TRUNCATE public.t_outbox_message CASCADE;
TRUNCATE admin_schema.t_migration_job CASCADE;

-- ---------- 清理旧的/异常的 audit_log 备份 ----------
TRUNCATE compliance_schema.t_audit_log_backup_20260610 CASCADE;
TRUNCATE compliance_schema.t_audit_log_backup_r131 CASCADE;

-- ============================================================================
-- Step 3: 重置所有业务表序列
-- ============================================================================
SELECT setval('req_schema.t_requirement_id_seq', 1, false);
SELECT setval('req_schema.t_requirement_pool_id_seq', 1, false);
SELECT setval('req_schema.t_test_case_id_seq', 1, false);
SELECT setval('req_schema.t_requirement_version_id_seq', 1, false);
SELECT setval('req_schema.t_review_id_seq', 1, false);
SELECT setval('req_schema.t_user_requirement_id_seq', 1, false);
SELECT setval('req_schema.t_product_requirement_id_seq', 1, false);
SELECT setval('req_schema.t_design_requirement_id_seq', 1, false);
SELECT setval('req_schema.t_system_requirement_id_seq', 1, false);

SELECT setval('trace_schema.t_requirement_relation_id_seq', 1, false);
SELECT setval('trace_schema.t_trace_link_id_seq', 1, false);
SELECT setval('trace_schema.t_trace_gap_ignored_id_seq', 1, false);
SELECT setval('trace_schema.t_requirement_test_case_id_seq', 1, false);

SELECT setval('chg_schema.t_change_request_id_seq', 1, false);
SELECT setval('chg_schema.t_change_approval_id_seq', 1, false);
SELECT setval('chg_schema.t_change_execution_id_seq', 1, false);
SELECT setval('chg_schema.t_change_timeline_id_seq', 1, false);
SELECT setval('chg_schema.t_impact_assessment_id_seq', 1, false);
SELECT setval('chg_schema.t_change_attachment_id_seq', 1, false);

SELECT setval('compliance_schema.t_audit_log_id_seq', 1, false);
SELECT setval('compliance_schema.t_baseline_id_seq', 1, false);
SELECT setval('compliance_schema.t_compliance_check_id_seq', 1, false);
SELECT setval('compliance_schema.t_dhf_evidence_id_seq', 1, false);
SELECT setval('compliance_schema.t_iec62304_checklist_id_seq', 1, false);
SELECT setval('compliance_schema.t_pr_correction_id_seq', 1, false);
SELECT setval('compliance_schema.t_problem_report_id_seq', 1, false);
SELECT setval('compliance_schema.t_report_config_id_seq', 1, false);
SELECT setval('compliance_schema.t_safety_classification_id_seq', 1, false);
SELECT setval('compliance_schema.t_soup_component_id_seq', 1, false);

SELECT setval('esign_schema.t_signature_intent_id_seq', 1, false);
SELECT setval('esign_schema.t_signature_record_id_seq', 1, false);
SELECT setval('esign_schema.t_signature_settings_id_seq', 1, false);

SELECT setval('risk_schema.t_risk_assessment_id_seq', 1, false);
SELECT setval('risk_schema.t_risk_matrix_id_seq', 1, false);
SELECT setval('risk_schema.t_risk_register_id_seq', 1, false);

SELECT setval('proj_schema.t_project_id_seq', 1, false);
SELECT setval('proj_schema.t_milestone_id_seq', 1, false);
SELECT setval('proj_schema.t_task_id_seq', 1, false);
SELECT setval('proj_schema.t_gantt_task_id_seq', 1, false);
SELECT setval('proj_schema.t_ipd_gate_id_seq', 1, false);
SELECT setval('proj_schema.t_project_deliverable_id_seq', 1, false);
SELECT setval('proj_schema.t_project_activity_id_seq', 1, false);
SELECT setval('proj_schema.t_project_member_id_seq', 1, false);
SELECT setval('proj_schema.t_compliance_template_id_seq', 1, false);

SELECT setval('prj_schema.t_task_predecessor_id_seq', 1, false);
SELECT setval('prj_schema.t_worklog_id_seq', 1, false);

SELECT setval('not_schema.t_notification_id_seq', 1, false);
SELECT setval('not_schema.t_email_queue_id_seq', 1, false);
SELECT setval('not_schema.t_im_queue_id_seq', 1, false);
SELECT setval('not_schema.t_notification_channel_id_seq', 1, false);
SELECT setval('not_schema.t_notification_settings_id_seq', 1, false);
SELECT setval('not_schema.t_notification_template_id_seq', 1, false);

SELECT setval('report_schema.t_report_id_seq', 1, false);

SELECT setval('admin_schema.t_migration_job_id_seq', 1, false);

-- ============================================================================
-- Step 4: 重新启用触发器
-- ============================================================================
SET session_replication_role = 'origin';

-- ============================================================================
-- Step 5: 重新插入种子业务数据
-- ============================================================================

-- ---------- 5a: init_database.sql 种子 ----------
-- 管理员用户（已存在，不会重复）
INSERT INTO sys_schema.t_user (username, password_hash, real_name, email, department, role, status)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '系统管理员', 'admin@medrms.com', 'IT', 'ADMIN', 'ACTIVE')
ON CONFLICT (username) DO NOTHING;

-- 字典数据（已存在，不会重复）
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

-- 示例项目
INSERT INTO proj_schema.t_project (project_no, project_name, description, status, manager_id, manager_name, start_date)
VALUES ('PRJ-ECG3-001', '心电监护仪 v3.0', '新一代心电监护仪软件开发项目', 'IN_PROGRESS', 1, '张工', '2026-01-01')
ON CONFLICT (project_no) DO NOTHING;

-- 示例需求（4条，URS/PRS/SRS/DRS）
INSERT INTO req_schema.t_requirement (requirement_no, requirement_type, project_id, title, description, priority, status)
VALUES
('URS-ECG3-0001', 'URS', (SELECT id FROM proj_schema.t_project WHERE project_no = 'PRJ-ECG3-001'), '心电信号采集需求', '系统应能采集心电信号，采样率不低于500Hz', 'MUST', 'Approved'),
('PRS-ECG3-0001', 'PRS', (SELECT id FROM proj_schema.t_project WHERE project_no = 'PRJ-ECG3-001'), '心电信号处理需求', '系统应能实时处理心电信号，延迟不超过100ms', 'MUST', 'Approved'),
('SRS-ECG3-0001', 'SRS', (SELECT id FROM proj_schema.t_project WHERE project_no = 'PRJ-ECG3-001'), '心电信号滤波算法', '实现低通滤波算法，截止频率可配置', 'MUST', 'InReview'),
('DRS-ECG3-0001', 'DRS', (SELECT id FROM proj_schema.t_project WHERE project_no = 'PRJ-ECG3-001'), 'DSP芯片驱动设计', '设计DSP芯片驱动接口', 'MUST', 'Draft')
ON CONFLICT (requirement_no) DO NOTHING;

-- 追溯关系（URS→PRS）
INSERT INTO trace_schema.t_requirement_relation (source_req_id, target_req_id, relation_type, project_id)
SELECT s.id, t.id, 'URS2PRS', (SELECT id FROM proj_schema.t_project WHERE project_no = 'PRJ-ECG3-001')
FROM req_schema.t_requirement s, req_schema.t_requirement t
WHERE s.requirement_no = 'URS-ECG3-0001' AND t.requirement_no = 'PRS-ECG3-0001'
ON CONFLICT DO NOTHING;

-- SOUP 组件
INSERT INTO compliance_schema.t_soup_component (component_name, component_code, version, supplier, supplier_country, software_category, compliance_standard, integration_level, risk_level, status)
VALUES
('FreeRTOS', 'FREERTOS', '11.0.0', 'Amazon', '美国', 'RTOS', 'IEC 62304', 'B', 'HIGH', 'ACTIVE'),
('lwIP', 'LWIP', '2.1.3', 'The lwIP Project', '美国', 'TCP/IP Stack', 'IEC 62304', 'B', 'MEDIUM', 'ACTIVE')
ON CONFLICT DO NOTHING;

-- ---------- 5b: 147_default_id1_data.sql 种子（适配实际 schema）----------
INSERT INTO proj_schema.t_project (id, project_no, project_name, description, status, manager_id, manager_name, created_at, updated_at, is_deleted)
VALUES (1, 'P-RMS-DEFAULT', '默认演示项目（RMS）', 'R116 补全的种子项目，供前端默认 ID 测试', 'ACTIVE', 1, '系统管理员', NOW(), NOW(), false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO req_schema.t_requirement (id, requirement_no, requirement_type, project_id, title, description, priority, status, version, created_by, created_at, updated_by, updated_at, is_deleted)
VALUES (1, 'REQ-RMS-DEFAULT-001', 'URS', 1, '默认演示需求（URS）', 'R116 补全的种子需求，供前端默认 ID 测试。', 'MUST', 'Draft', 1, 1, NOW(), 1, NOW(), false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO chg_schema.t_change_request (id, change_no, title, description, change_type, status, requester_id, assignee_id, created_at, updated_at, is_deleted)
VALUES (1, 'CHG-RMS-DEFAULT-001', '默认演示变更', 'R116 补全的种子变更，供前端默认 ID 测试。', 'CORRECTION', 'DRAFT', 1, 1, NOW(), NOW(), false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO risk_schema.t_risk_register (id, project_id, risk_no, risk_title, description, severity, probability, status, owner_id, created_at, updated_at, is_deleted)
VALUES (1, 1, 'RISK-RMS-DEFAULT-001', '默认演示风险', 'R116 补全的种子风险，供前端默认 ID 测试。', 'MEDIUM', 'MEDIUM', 'OPEN', 1, NOW(), NOW(), false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO proj_schema.t_project_deliverable (id, project_id, name, description, status, owner_id, created_at, updated_at, is_deleted)
VALUES (1, 1, '默认演示交付物', 'R116 补全的种子交付物', 'DRAFT', 1, NOW(), NOW(), false)
ON CONFLICT (id) DO NOTHING;

-- ---------- 5c: r150_seed_minimal.sql 种子 ----------
INSERT INTO proj_schema.t_project (id, project_no, project_name, description, status, manager_id, manager_name, start_date, end_date)
VALUES (100, 'PRJ-R150-TEST', 'R150 集成测试专用项目', '集成测试期间使用的项目，id=100 与 demo 解耦', 'IN_PROGRESS', 1, 'admin', '2026-07-01', '2026-12-31')
ON CONFLICT (id) DO UPDATE SET project_name = EXCLUDED.project_name;

INSERT INTO proj_schema.t_project_member (project_id, project_no, user_id, username, real_name, role, department, joined_at, status)
VALUES (100, 'PRJ-R150-TEST', 1, 'admin', '管理员', 'PROJECT_MANAGER', '测试部', '2026-07-01', 'ACTIVE')
ON CONFLICT DO NOTHING;

INSERT INTO proj_schema.t_milestone (milestone_no, name, project_id, gate_type, planned_date, status)
VALUES
('MS-R150-01', 'R150 概念门', 100, 'DCP1', '2026-07-15', 'COMPLETED'),
('MS-R150-02', 'R150 计划门', 100, 'DCP2', '2026-08-15', 'IN_PROGRESS'),
('MS-R150-03', 'R150 设计门', 100, 'DCP3', '2026-09-15', 'PLANNED'),
('MS-R150-04', 'R150 开发门', 100, 'DCP4', '2026-10-15', 'PLANNED')
ON CONFLICT (milestone_no) DO NOTHING;

INSERT INTO proj_schema.t_task (task_no, title, description, project_id, assignee_id, assignee_name, start_date, end_date, estimated_hours, status, priority)
VALUES
('T-R150-001', '需求基线准备', '为 R150 测试准备 3 条 URS 基线需求', 100, 1, 'admin', '2026-07-01', '2026-07-05', 16, 'TODO', 'HIGH'),
('T-R150-002', '追溯链建立', '建立 URS→SDS→TC 三层追溯', 100, 1, 'admin', '2026-07-06', '2026-07-10', 24, 'TODO', 'HIGH'),
('T-R150-003', '基线锁定', '基线锁定 + 电子签名验证', 100, 1, 'admin', '2026-07-11', '2026-07-15', 8, 'TODO', 'HIGH')
ON CONFLICT (task_no) DO NOTHING;

INSERT INTO req_schema.t_requirement (requirement_no, requirement_type, project_id, title, description, priority, requirement_category, source, created_by)
VALUES
('URS-R150-001', 'URS', 100, '心电数据采集（R150 测试 URS）', '心电监护仪基础数据采集功能需求', 'MUST', 'SOFTWARE', 'INTERNAL', 1),
('URS-R150-002', 'URS', 100, '报警阈值管理（R150 测试 URS）', '报警阈值设定与触发逻辑', 'MUST', 'SOFTWARE', 'INTERNAL', 1),
('URS-R150-003', 'URS', 100, '数据导出 PDF（R150 测试 URS）', '病历数据导出 PDF 文件', 'SHOULD', 'SOFTWARE', 'INTERNAL', 1)
ON CONFLICT (requirement_no) DO UPDATE SET title = EXCLUDED.title;

-- ---------- 5d: 重建 ancestor 闭包表（从 t_requirement_relation 递归计算）----------
WITH RECURSIVE ancestor_tree AS (
  -- 自引用（depth=0）
  SELECT r.id AS ancestor_id, r.id AS descendant_id, 0 AS depth
  FROM req_schema.t_requirement r

  UNION ALL

  -- 递归：parent → child（t_requirement_relation 的 source→target）
  SELECT at.ancestor_id, rel.target_req_id, at.depth + 1
  FROM ancestor_tree at
  JOIN trace_schema.t_requirement_relation rel ON at.descendant_id = rel.source_req_id
  WHERE at.depth < 100
)
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth)
SELECT DISTINCT ancestor_id, descendant_id, depth
FROM ancestor_tree
ON CONFLICT DO NOTHING;

-- ---------- 5e: 修复序列 ----------
SELECT setval(pg_get_serial_sequence('req_schema.t_requirement', 'id'),
              GREATEST((SELECT MAX(id) FROM req_schema.t_requirement), 1));
SELECT setval(pg_get_serial_sequence('proj_schema.t_project', 'id'),
              GREATEST((SELECT MAX(id) FROM proj_schema.t_project), 1));
SELECT setval(pg_get_serial_sequence('risk_schema.t_risk_register', 'id'),
              GREATEST((SELECT MAX(id) FROM risk_schema.t_risk_register), 1));
SELECT setval(pg_get_serial_sequence('chg_schema.t_change_request', 'id'),
              GREATEST((SELECT MAX(id) FROM chg_schema.t_change_request), 1));
SELECT setval(pg_get_serial_sequence('proj_schema.t_project_deliverable', 'id'),
              GREATEST((SELECT MAX(id) FROM proj_schema.t_project_deliverable), 1));

COMMIT;

-- ============================================================================
-- Step 6: 验证清理结果
-- ============================================================================
\echo '=== 清理完成 — 验证数据 ==='
SELECT 'proj_schema.t_project' AS tbl, COUNT(*) AS cnt FROM proj_schema.t_project
UNION ALL SELECT 'req_schema.t_requirement', COUNT(*) FROM req_schema.t_requirement
UNION ALL SELECT 'chg_schema.t_change_request', COUNT(*) FROM chg_schema.t_change_request
UNION ALL SELECT 'risk_schema.t_risk_register', COUNT(*) FROM risk_schema.t_risk_register
UNION ALL SELECT 'proj_schema.t_project_deliverable', COUNT(*) FROM proj_schema.t_project_deliverable
UNION ALL SELECT 'compliance_schema.t_soup_component', COUNT(*) FROM compliance_schema.t_soup_component
UNION ALL SELECT 'compliance_schema.t_audit_log', COUNT(*) FROM compliance_schema.t_audit_log
UNION ALL SELECT 'sys_schema.t_user', COUNT(*) FROM sys_schema.t_user
UNION ALL SELECT 'sys_schema.t_role', COUNT(*) FROM sys_schema.t_role
UNION ALL SELECT 'sys_schema.t_role_permission', COUNT(*) FROM sys_schema.t_role_permission
UNION ALL SELECT 'sys_schema.t_dict_item', COUNT(*) FROM sys_schema.t_dict_item
UNION ALL SELECT 'not_schema.t_notification', COUNT(*) FROM not_schema.t_notification
ORDER BY tbl;
