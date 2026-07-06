-- =============================================================================
-- R159 修复：opencode 测试发现 F2/F4/F9
--   F2: pd 用户 DB 不存在 → 补 pd 用户
--   F4: reviewer 缺 baseline:list 权限 → 加权限
--   F9: SOUP/CAPA/ProjectDeliverables 缺种子数据 → 补种子
-- 验证方式：登录测试 + curl 重测
-- =============================================================================

BEGIN;

-- ===================================================
-- F2 修复：补 PD 用户（id=9 角色，密码 admin123）
-- 用现有 qa_mgr 的 bcrypt hash（同 admin123）
-- ===================================================
INSERT INTO sys_schema.t_user
    (username, password_hash, real_name, email, department, role, status, last_login_at, created_at, updated_at, is_deleted)
VALUES
    ('pd',
     '$2b$10$3tCe7It.jXtlieZStLN/OuTpHamNIQvydNmriEaaYW3GOYXE/uvt2',  -- admin123 哈希
     '产品经理-吴十',
     'pd@medrms.com',
     '产品部',
     'PD',
     'ACTIVE',
     NULL,
     NOW(), NOW(), false)
ON CONFLICT (username) DO NOTHING;

-- 同时建 t_user_role 关联（pd user_id + PD role_id=9）
INSERT INTO sys_schema.t_user_role (user_id, role_id)
SELECT u.id, 9 FROM sys_schema.t_user u WHERE u.username='pd'
ON CONFLICT DO NOTHING;

-- ===================================================
-- F4 修复：reviewer 角色加 baseline:list 权限
-- 实际上 reviewer 应该能查看基线（评审需要），加 baseline:list 即可
-- ===================================================
INSERT INTO sys_schema.t_role_permission (role_id, perm_id)
SELECT 5, p.id FROM sys_schema.t_permission p WHERE p.perm_code='baseline:list'
ON CONFLICT DO NOTHING;

-- ===================================================
-- F9 修复：补 SOUP 组件种子数据（t_soup_component）
-- SOUP = Software of Unknown Provenance，医疗设备常用现成软件
-- ===================================================
INSERT INTO compliance_schema.t_soup_component
    (component_name, component_code, version, supplier, supplier_country, software_category, compliance_standard, usage_scenario, integration_level, risk_level, license_type, created_at)
SELECT 'Apache Tomcat', 'TOMCAT-9.0', '9.0.65', 'Apache Software Foundation', 'USA',
     'WEB_SERVER', 'IEC 62304 Class B', '心电监护仪 Web 后端', 'MEDIUM', 'MEDIUM', 'APACHE-2.0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM compliance_schema.t_soup_component WHERE component_code='TOMCAT-9.0');

INSERT INTO compliance_schema.t_soup_component
    (component_name, component_code, version, supplier, supplier_country, software_category, compliance_standard, usage_scenario, integration_level, risk_level, license_type, created_at)
SELECT 'OpenSSL', 'OPENSSL-3.0', '3.0.8', 'OpenSSL Project', 'Global',
     'CRYPTO_LIB', 'NIST FIPS 140-2', '心电数据加密传输', 'DEEP', 'HIGH', 'APACHE-2.0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM compliance_schema.t_soup_component WHERE component_code='OPENSSL-3.0');

INSERT INTO compliance_schema.t_soup_component
    (component_name, component_code, version, supplier, supplier_country, software_category, compliance_standard, usage_scenario, integration_level, risk_level, license_type, created_at)
SELECT 'PostgreSQL JDBC Driver', 'PGJDBC-42', '42.7.1', 'PostgreSQL Global Dev Group', 'Global',
     'DB_DRIVER', 'N/A', '数据库连接', 'DEEP', 'LOW', 'BSD-2', NOW()
WHERE NOT EXISTS (SELECT 1 FROM compliance_schema.t_soup_component WHERE component_code='PGJDBC-42');

INSERT INTO compliance_schema.t_soup_component
    (component_name, component_code, version, supplier, supplier_country, software_category, compliance_standard, usage_scenario, integration_level, risk_level, license_type, created_at)
SELECT 'MyBatis-Plus', 'MYBATIS-PLUS', '3.5.5', 'baomidou', 'China',
     'ORM_FRAMEWORK', 'N/A', '数据访问层', 'DEEP', 'LOW', 'APACHE-2.0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM compliance_schema.t_soup_component WHERE component_code='MYBATIS-PLUS');

INSERT INTO compliance_schema.t_soup_component
    (component_name, component_code, version, supplier, supplier_country, software_category, compliance_standard, usage_scenario, integration_level, risk_level, license_type, created_at)
SELECT 'Redis (服务端)', 'REDIS-7.0', '7.0.12', 'Redis Labs', 'USA',
     'CACHE_DB', 'N/A', '会话缓存 + 通知队列', 'DEEP', 'MEDIUM', 'BSD-3', NOW()
WHERE NOT EXISTS (SELECT 1 FROM compliance_schema.t_soup_component WHERE component_code='REDIS-7.0');

-- ===================================================
-- F9 修复：补 t_pr_correction 种子（先需要 t_problem_report）
-- 先查 t_problem_report 表结构
-- ===================================================
-- t_pr_correction 需要 problem_report_id (NOT NULL)，先看 t_problem_report 是否存在
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='compliance_schema' AND table_name='t_problem_report') THEN
        -- 插入 problem_report（如果空）
        INSERT INTO compliance_schema.t_problem_report
            (report_code, project_id, project_name, title, severity, description, status,
             discovery_date, source_type, reporter_id, reporter_name, is_deleted, created_at, updated_at)
        SELECT 'PR-R150-001', 100, 'PRJ-R150-TEST', '心电数据采集中断 30s',
               'MAJOR', '系统在 2026-05-12 14:23 出现 30 秒数据采集中断',
               'INVESTIGATING', NOW(), 'TEST', 1, 'admin', false, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM compliance_schema.t_problem_report WHERE report_code='PR-R150-001');

        -- 插入 PR Correction (CAPA)
        INSERT INTO compliance_schema.t_pr_correction
            (problem_report_id, action, owner_id, due_date, status, created_at)
        SELECT pr.id, '增加网络重试机制 + 心跳监控', 1, NOW() + INTERVAL '30 days',
               'IN_PROGRESS', NOW()
        FROM compliance_schema.t_problem_report pr
        WHERE pr.report_code='PR-R150-001'
          AND NOT EXISTS (SELECT 1 FROM compliance_schema.t_pr_correction WHERE problem_report_id=pr.id);
    END IF;
END $$;

-- ===================================================
-- F9 修复：补 t_project_deliverable 种子
-- ===================================================
INSERT INTO proj_schema.t_project_deliverable
    (project_id, name, type, phase, status, owner_id, owner_name, due_date, description, is_deleted, created_at, updated_at)
SELECT 100, 'R150 集成测试报告', 'DOCUMENT', 'DCP3', 'COMPLETED', 1, 'admin',
       NOW(), 'R150 跨模块集成测试报告 v1.0', false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM proj_schema.t_project_deliverable WHERE project_id=100 AND name='R150 集成测试报告');

INSERT INTO proj_schema.t_project_deliverable
    (project_id, name, type, phase, status, owner_id, owner_name, due_date, description, is_deleted, created_at, updated_at)
SELECT 100, '心电数据采集模块代码', 'SOFTWARE', 'DCP4', 'IN_PROGRESS', 1, 'admin',
       NOW() + INTERVAL '14 days', '心电信号采集处理源代码 + 单元测试', false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM proj_schema.t_project_deliverable WHERE project_id=100 AND name='心电数据采集模块代码');

COMMIT;

-- ===================================================
-- 验证
-- ===================================================
-- pd 用户
SELECT 'F2 验证' AS check_name, count(*) AS pd_users FROM sys_schema.t_user WHERE username='pd';
-- reviewer baseline:list 权限
SELECT 'F4 验证' AS check_name, count(*) AS reviewer_baseline_perm
FROM sys_schema.t_role_permission rp
JOIN sys_schema.t_permission p ON rp.perm_id=p.id
WHERE rp.role_id=5 AND p.perm_code='baseline:list';
-- SOUP 数据
SELECT 'F9 SOUP' AS check_name, count(*) AS soup_components FROM compliance_schema.t_soup_component;
SELECT 'F9 PR Correction' AS check_name, count(*) AS pr_corrections FROM compliance_schema.t_pr_correction;
SELECT 'F9 Deliverables' AS check_name, count(*) AS project_deliverables FROM proj_schema.t_project_deliverable;