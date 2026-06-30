-- ============================================================================
-- DDL 147: 种子数据补全（ID=1 默认数据）— R116 P1-02 修复
-- 2026-06-30 QClaw
--
-- 背景：
--   1. R114 测试发现 /requirements/1、/changes/1、/projects/1 等默认 ID 数据缺失
--   2. 导致测试时无法直接访问默认 ID；用户首次访问可能遇到 404
--   3. R116 修复：补全默认 ID=1 的种子数据
--
-- 验证：
--   SELECT id, requirement_no, title, status FROM req_schema.t_requirement WHERE id = 1;
--   SELECT id, change_no, title, status FROM chg_schema.t_change_request WHERE id = 1;
--   SELECT id, project_no, project_name FROM proj_schema.t_project WHERE id = 1;
-- ============================================================================

-- 1. 项目 ID=1（用于 FK 引用）
INSERT INTO proj_schema.t_project (id, project_no, project_name, project_type, status, description,
                                   created_by, created_at, updated_by, updated_at, is_deleted)
VALUES (1, 'P-RMS-DEFAULT', '默认演示项目（RMS）', 'PRODUCT', 'ACTIVE',
        'R116 补全的种子项目，供前端默认 ID 测试',
        1, NOW(), 1, NOW(), false)
ON CONFLICT (id) DO NOTHING;

-- 2. 需求 ID=1（URS 级别）
INSERT INTO req_schema.t_requirement (id, requirement_no, requirement_type, project_id, title,
                                     description, priority, status, version,
                                     created_by, created_at, updated_by, updated_at, is_deleted)
VALUES (1, 'REQ-RMS-DEFAULT-001', 'URS', 1, '默认演示需求（URS）',
        'R116 补全的种子需求，供前端默认 ID 测试。',
        'MUST', 'Draft', 1,
        1, NOW(), 1, NOW(), false)
ON CONFLICT (id) DO NOTHING;

-- 3. 变更 ID=1
INSERT INTO chg_schema.t_change_request (id, change_no, title, description, project_id,
                                         change_type, status, priority, created_by,
                                         created_at, updated_by, updated_at, is_deleted)
VALUES (1, 'CHG-RMS-DEFAULT-001', '默认演示变更', 'R116 补全的种子变更，供前端默认 ID 测试。',
        1, 'CORRECTION', 'DRAFT', 'MEDIUM',
        1, NOW(), 1, NOW(), false)
ON CONFLICT (id) DO NOTHING;

-- 4. 风险 ID=1
INSERT INTO risk_schema.t_risk_register (id, project_id, requirement_id, risk_no, title,
                                          description, severity, probability, rpn, status,
                                          created_by, created_at, updated_by, updated_at, is_deleted)
VALUES (1, 1, 1, 'RISK-RMS-DEFAULT-001', '默认演示风险',
        'R116 补全的种子风险，供前端默认 ID 测试。',
        'MEDIUM', 'MEDIUM', 9, 'OPEN',
        1, NOW(), 1, NOW(), false)
ON CONFLICT (id) DO NOTHING;

-- 5. 项目交付物 ID=1
INSERT INTO proj_schema.t_project_deliverable (id, project_id, deliverable_type, name, description,
                                                status, version, created_by, created_at,
                                                updated_by, updated_at, is_deleted)
VALUES (1, 1, 'DOCUMENT', '默认演示交付物', 'R116 补全的种子交付物',
        'DRAFT', '1.0', 1, NOW(), 1, NOW(), false)
ON CONFLICT (id) DO NOTHING;

-- 6. 验证：确认所有 ID=1 数据存在
SELECT 't_project' AS table_name, COUNT(*) AS id1_exists FROM proj_schema.t_project WHERE id = 1
UNION ALL
SELECT 't_requirement', COUNT(*) FROM req_schema.t_requirement WHERE id = 1
UNION ALL
SELECT 't_change_request', COUNT(*) FROM chg_schema.t_change_request WHERE id = 1
UNION ALL
SELECT 't_risk_register', COUNT(*) FROM risk_schema.t_risk_register WHERE id = 1
UNION ALL
SELECT 't_project_deliverable', COUNT(*) FROM proj_schema.t_project_deliverable WHERE id = 1;

COMMENT ON TABLE req_schema.t_requirement IS '需求表 | R116: 默认 ID=1 种子数据已补全 (DDL 147)';
COMMENT ON TABLE chg_schema.t_change_request IS '变更申请表 | R116: 默认 ID=1 种子数据已补全 (DDL 147)';
COMMENT ON TABLE risk_schema.t_risk_register IS '风险登记表 | R116: 默认 ID=1 种子数据已补全 (DDL 147)';