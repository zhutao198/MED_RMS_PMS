-- =============================================================================
-- R150 最小种子数据集（修复 test_data_full_flow.sql 第 75 行 milestone_no NOT NULL 中断）
-- 设计原则：仅插入测试必需的"地基"数据；测试脚本自己创建动态数据
-- 用法：psql -U postgres -d med_rms_pms -f r150_seed_minimal.sql
-- =============================================================================

-- 1. 项目（如已存在则跳过，约定 id=100 专供 R150 测试避免与 demo id 冲突）
INSERT INTO proj_schema.t_project (id, project_no, project_name, description, status, manager_id, manager_name, start_date, end_date)
VALUES
(100, 'PRJ-R150-TEST', 'R150 集成测试专用项目', '集成测试期间使用的项目，id=100 与 demo 解耦', 'IN_PROGRESS', 1, 'admin', '2026-07-01', '2026-12-31')
ON CONFLICT (id) DO UPDATE SET project_name = EXCLUDED.project_name;

-- 2. 项目成员（admin 加入）
INSERT INTO proj_schema.t_project_member (project_id, project_no, user_id, username, real_name, role, department, joined_at, status)
VALUES
(100, 'PRJ-R150-TEST', 1, 'admin', '管理员', 'PROJECT_MANAGER', '测试部', '2026-07-01', 'ACTIVE')
ON CONFLICT DO NOTHING;

-- 3. 里程碑（DCP1~5，含 milestone_no 必须值）
INSERT INTO proj_schema.t_milestone (milestone_no, name, project_id, gate_type, planned_date, status)
VALUES
('MS-R150-01', 'R150 概念门', 100, 'DCP1', '2026-07-15', 'COMPLETED'),
('MS-R150-02', 'R150 计划门', 100, 'DCP2', '2026-08-15', 'IN_PROGRESS'),
('MS-R150-03', 'R150 设计门', 100, 'DCP3', '2026-09-15', 'PLANNED'),
('MS-R150-04', 'R150 开发门', 100, 'DCP4', '2026-10-15', 'PLANNED')
ON CONFLICT (milestone_no) DO NOTHING;

-- 4. 任务（含 task_no、title、project_id 必须值）
INSERT INTO proj_schema.t_task (task_no, title, description, project_id, assignee_id, assignee_name, start_date, end_date, estimated_hours, status, priority)
VALUES
('T-R150-001', '需求基线准备', '为 R150 测试准备 3 条 URS 基线需求', 100, 1, 'admin', '2026-07-01', '2026-07-05', 16, 'TODO', 'HIGH'),
('T-R150-002', '追溯链建立', '建立 URS→SDS→TC 三层追溯', 100, 1, 'admin', '2026-07-06', '2026-07-10', 24, 'TODO', 'HIGH'),
('T-R150-003', '基线锁定', '基线锁定 + 电子签名验证', 100, 1, 'admin', '2026-07-11', '2026-07-15', 8, 'TODO', 'HIGH')
ON CONFLICT (task_no) DO NOTHING;

-- 5. 最小 3 个 URS 需求（供链路 C URS→SDS→TC 使用）
INSERT INTO req_schema.t_requirement (requirement_no, requirement_type, project_id, title, description, priority, requirement_category, source, created_by)
VALUES
('URS-R150-001', 'URS', 100, '心电数据采集（R150 测试 URS）', '心电监护仪基础数据采集功能需求', 'MUST', 'SOFTWARE', 'INTERNAL', 1),
('URS-R150-002', 'URS', 100, '报警阈值管理（R150 测试 URS）', '报警阈值设定与触发逻辑', 'MUST', 'SOFTWARE', 'INTERNAL', 1),
('URS-R150-003', 'URS', 100, '数据导出 PDF（R150 测试 URS）', '病历数据导出 PDF 文件', 'SHOULD', 'SOFTWARE', 'INTERNAL', 1)
ON CONFLICT (requirement_no) DO UPDATE SET title = EXCLUDED.title;

SELECT setval(pg_get_serial_sequence('req_schema.t_requirement', 'id'),
              GREATEST((SELECT MAX(id) FROM req_schema.t_requirement), 1));
