-- ============================================================================
-- 121. RBAC 种子数据（v1.24 R25）
-- 8 角色 + 62 权限码 + 关联 + 8 类测试用户
-- 设计依据：Detailed/04-权限设计/权限流程设计.md 1.2/1.3 节
-- ============================================================================

-- 清理旧种子（保持幂等）
TRUNCATE sys_schema.t_user_role RESTART IDENTITY CASCADE;
TRUNCATE sys_schema.t_role_permission RESTART IDENTITY CASCADE;
DELETE FROM sys_schema.t_permission;
DELETE FROM sys_schema.t_role WHERE role_code IN ('ADMIN','QA_MGR','PM','RE','REVIEWER','RISK_MGR','COMPLIANCE','VIEWER');

-- 8 角色
INSERT INTO sys_schema.t_role (role_name, role_code, description, status) VALUES
('系统管理员', 'ADMIN',      '全系统管理：用户/角色/配置/字典',  'ACTIVE'),
('QA经理',     'QA_MGR',     '评审/合规/审计/签名/基线管理',    'ACTIVE'),
('项目经理',   'PM',         '项目级管理：需求审批/基线/门控',  'ACTIVE'),
('需求工程师', 'RE',         '需求创建/编辑/追溯',              'ACTIVE'),
('评审专家',   'REVIEWER',   '需求评审/签名确认',               'ACTIVE'),
('风险管理',   'RISK_MGR',   '风险创建/分析/控制措施/状态变更', 'ACTIVE'),
('合规人员',   'COMPLIANCE', '合规审计/SOUP/安全分类',          'ACTIVE'),
('只读用户',   'VIEWER',     '查看/导出',                       'ACTIVE');

-- 62 权限码（按模块分组）
-- sys 系列（5）
INSERT INTO sys_schema.t_permission (perm_code, perm_name, perm_type) VALUES
('sys:user:list',     '用户管理',     'MENU'),
('sys:role:list',     '角色管理',     'MENU'),
('sys:config:list',   '系统配置',     'MENU'),
('sys:dict:list',     '字典管理',     'MENU'),
('sys:org:list',      '组织架构',     'MENU');

-- proj 系列（4）
INSERT INTO sys_schema.t_permission (perm_code, perm_name, perm_type) VALUES
('proj:list',         '项目列表',     'MENU'),
('proj:create',       '创建项目',     'BUTTON'),
('proj:update',       '编辑项目',     'BUTTON'),
('proj:member',       '项目成员',     'BUTTON');

-- req 系列（8）
INSERT INTO sys_schema.t_permission (perm_code, perm_name, perm_type) VALUES
('req:list',          '需求列表',     'MENU'),
('req:create',        '创建需求',     'BUTTON'),
('req:update',        '编辑需求',     'BUTTON'),
('req:delete',        '删除需求',     'BUTTON'),
('req:submit',        '提交需求',     'BUTTON'),
('req:review',        '评审需求',     'BUTTON'),
('req:status',        '变更需求状态', 'BUTTON'),
('req:import',        '导入需求',     'BUTTON');

-- trace 系列（4）
INSERT INTO sys_schema.t_permission (perm_code, perm_name, perm_type) VALUES
('trace:list',        '追溯列表',     'MENU'),
('trace:create',      '创建追溯',     'BUTTON'),
('trace:delete',      '删除追溯',     'BUTTON'),
('trace:matrix',      '追溯矩阵',     'MENU');

-- trace:gaps / trace:coverage（设计稿仅有 trace:matrix, trace:gaps 两行 + 复盖率行）
INSERT INTO sys_schema.t_permission (perm_code, perm_name, perm_type) VALUES
('trace:coverage',    '追溯覆盖率',   'MENU'),
('trace:gaps',        '追溯缺口',     'MENU');

-- chg 系列（5）
INSERT INTO sys_schema.t_permission (perm_code, perm_name, perm_type) VALUES
('chg:list',          '变更列表',     'MENU'),
('chg:create',        '创建变更',     'BUTTON'),
('chg:analyze',       '变更影响分析', 'BUTTON'),
('chg:approve',       '变更审批',     'BUTTON'),
('chg:execute',       '变更执行',     'BUTTON');

-- audit 系列（2）
INSERT INTO sys_schema.t_permission (perm_code, perm_name, perm_type) VALUES
('audit:read',        '查看审计日志', 'MENU'),
('audit:verify',      '校验审计日志', 'BUTTON');

-- soup 系列（4）
INSERT INTO sys_schema.t_permission (perm_code, perm_name, perm_type) VALUES
('soup:list',         'SOUP列表',     'MENU'),
('soup:create',       '新增SOUP',     'BUTTON'),
('soup:update',       '更新SOUP',     'BUTTON'),
('soup:review',       '评审SOUP',     'BUTTON');

-- safety 系列（2）
INSERT INTO sys_schema.t_permission (perm_code, perm_name, perm_type) VALUES
('safety:read',       '查看安全分类', 'MENU'),
('safety:create',     '创建安全分类', 'BUTTON');

-- baseline 系列（5）
INSERT INTO sys_schema.t_permission (perm_code, perm_name, perm_type) VALUES
('baseline:list',     '基线列表',     'MENU'),
('baseline:create',   '创建基线',     'BUTTON'),
('baseline:lock',     '锁定基线',     'BUTTON'),
('baseline:unlock',   '解锁基线',     'BUTTON'),
('baseline:compare',  '基线对比',     'BUTTON');

-- esign 系列（6）
INSERT INTO sys_schema.t_permission (perm_code, perm_name, perm_type) VALUES
('esign:intent',      '签名意图',     'BUTTON'),
('esign:sign',        '执行签名',     'BUTTON'),
('esign:read',        '查看签名',     'MENU'),
('esign:verify',      '校验签名',     'BUTTON'),
('esign:pwd',         '签名密码验证', 'BUTTON'),
('esign:otp',         'OTP验证',      'BUTTON');

-- risk 系列（6）
INSERT INTO sys_schema.t_permission (perm_code, perm_name, perm_type) VALUES
('risk:list',         '风险列表',     'MENU'),
('risk:create',       '创建风险',     'BUTTON'),
('risk:update',       '编辑风险',     'BUTTON'),
('risk:analyze',      '风险分析',     'BUTTON'),
('risk:control',      '风险控制',     'BUTTON'),
('risk:status',       '变更风险状态', 'BUTTON');

-- proj:gate:review（1）
INSERT INTO sys_schema.t_permission (perm_code, perm_name, perm_type) VALUES
('proj:gate:review',  'DCP门控评审',  'BUTTON');

-- pr 系列（4，问题报告）
INSERT INTO sys_schema.t_permission (perm_code, perm_name, perm_type) VALUES
('pr:list',           '问题报告列表', 'MENU'),
('pr:create',         '创建问题报告', 'BUTTON'),
('pr:status',         '变更问题状态', 'BUTTON'),
('pr:correction',     '问题纠正',     'BUTTON');

-- report 系列（3）
INSERT INTO sys_schema.t_permission (perm_code, perm_name, perm_type) VALUES
('report:dashboard',  '仪表盘',       'MENU'),
('report:stats',      '统计报表',     'MENU'),
('report:export',     '导出报表',     'BUTTON');

-- compliance 系列（1）
INSERT INTO sys_schema.t_permission (perm_code, perm_name, perm_type) VALUES
('compliance:iec62304','IEC62304检查', 'MENU');

-- regulation 系列（1）
INSERT INTO sys_schema.t_permission (perm_code, perm_name, perm_type) VALUES
('regulation:read',   '查看法规',     'MENU');

-- ============ 角色-权限关联 ============
-- ADMIN：全部 62 权限
INSERT INTO sys_schema.t_role_permission (role_id, perm_id)
SELECT r.id, p.id FROM sys_schema.t_role r, sys_schema.t_permission p WHERE r.role_code = 'ADMIN';

-- QA_MGR：除 sys:user:list / sys:role:list / sys:config:list / sys:dict:list 外的全部
-- 设计稿：sys:org:list ✅；baseline:lock ⭐；baseline:unlock ⭐
INSERT INTO sys_schema.t_role_permission (role_id, perm_id)
SELECT r.id, p.id FROM sys_schema.t_role r, sys_schema.t_permission p
WHERE r.role_code = 'QA_MGR'
  AND p.perm_code NOT IN ('sys:user:list','sys:role:list','sys:config:list','sys:dict:list');

-- PM：sys:org:list ✅；proj:*, req:* (除 delete,import 受限), trace:*, chg:*, baseline:create/compare, esign:intent/sign/read/verify/pwd/otp, risk:*, proj:gate:review ⭐
INSERT INTO sys_schema.t_role_permission (role_id, perm_id)
SELECT r.id, p.id FROM sys_schema.t_role r, sys_schema.t_permission p
WHERE r.role_code = 'PM'
  AND (
    p.perm_code IN ('sys:org:list','proj:list','proj:create','proj:update','proj:member',
                    'req:list','req:create','req:update','req:delete','req:submit','req:review','req:status','req:import',
                    'trace:list','trace:create','trace:delete','trace:matrix','trace:coverage','trace:gaps',
                    'chg:list','chg:create','chg:analyze','chg:approve','chg:execute',
                    'soup:list','safety:read',
                    'baseline:list','baseline:create','baseline:compare',
                    'esign:intent','esign:sign','esign:read','esign:verify','esign:pwd','esign:otp',
                    'risk:list','risk:create','risk:update','risk:control','risk:status',
                    'proj:gate:review',
                    'pr:list','pr:create','pr:status','pr:correction',
                    'report:dashboard','report:stats','report:export',
                    'compliance:iec62304','regulation:read')
  );

-- RE：proj:list, req:list/create/submit/import, trace:*, chg:list/create, soup:list, safety:read, baseline:list/compare, esign:read/verify
INSERT INTO sys_schema.t_role_permission (role_id, perm_id)
SELECT r.id, p.id FROM sys_schema.t_role r, sys_schema.t_permission p
WHERE r.role_code = 'RE'
  AND p.perm_code IN ('proj:list',
                      'req:list','req:create','req:submit','req:import',
                      'trace:list','trace:create','trace:matrix','trace:coverage','trace:gaps',
                      'chg:list','chg:create',
                      'soup:list','safety:read',
                      'baseline:list','baseline:compare',
                      'esign:read','esign:verify',
                      'risk:list',
                      'pr:list',
                      'report:dashboard','report:stats','report:export',
                      'regulation:read');

-- REVIEWER：req:list/review, trace:list/matrix/coverage/gaps, esign:intent/sign/read/verify/pwd/otp
INSERT INTO sys_schema.t_role_permission (role_id, perm_id)
SELECT r.id, p.id FROM sys_schema.t_role r, sys_schema.t_permission p
WHERE r.role_code = 'REVIEWER'
  AND p.perm_code IN ('req:list','req:review',
                      'trace:list','trace:matrix','trace:coverage','trace:gaps',
                      'esign:intent','esign:sign','esign:read','esign:verify','esign:pwd','esign:otp');

-- RISK_MGR：proj:list, req:list, trace:list/matrix/coverage/gaps, chg:list/analyze, soup:list, safety:*, baseline:list/compare, esign:read/verify, risk:*, pr:list/create/status/correction, report:*
INSERT INTO sys_schema.t_role_permission (role_id, perm_id)
SELECT r.id, p.id FROM sys_schema.t_role r, sys_schema.t_permission p
WHERE r.role_code = 'RISK_MGR'
  AND p.perm_code IN ('proj:list',
                      'req:list',
                      'trace:list','trace:matrix','trace:coverage','trace:gaps',
                      'chg:list','chg:analyze',
                      'soup:list','safety:read','safety:create',
                      'baseline:list','baseline:compare',
                      'esign:read','esign:verify',
                      'risk:list','risk:create','risk:update','risk:analyze','risk:control','risk:status',
                      'pr:list','pr:create','pr:status','pr:correction',
                      'report:dashboard','report:stats','report:export');

-- COMPLIANCE：sys:org:list, proj:list, req:list, trace:list/matrix/coverage/gaps, chg:list, audit:*, soup:*, safety:*, baseline:list/compare, esign:read/verify, pr:list/create/status/correction, report:*, compliance:*, regulation:read
INSERT INTO sys_schema.t_role_permission (role_id, perm_id)
SELECT r.id, p.id FROM sys_schema.t_role r, sys_schema.t_permission p
WHERE r.role_code = 'COMPLIANCE'
  AND p.perm_code IN ('sys:org:list',
                      'proj:list',
                      'req:list',
                      'trace:list','trace:matrix','trace:coverage','trace:gaps',
                      'chg:list',
                      'audit:read','audit:verify',
                      'soup:list','soup:create','soup:update','soup:review',
                      'safety:read','safety:create',
                      'baseline:list','baseline:compare',
                      'esign:read','esign:verify',
                      'pr:list','pr:create','pr:status','pr:correction',
                      'report:dashboard','report:stats','report:export',
                      'compliance:iec62304','regulation:read');

-- VIEWER：proj:list, req:list, trace:list/matrix/coverage/gaps, chg:list, soup:list, safety:read, baseline:list/compare, esign:read/verify, risk:list, pr:list, report:*, regulation:read
INSERT INTO sys_schema.t_role_permission (role_id, perm_id)
SELECT r.id, p.id FROM sys_schema.t_role r, sys_schema.t_permission p
WHERE r.role_code = 'VIEWER'
  AND p.perm_code IN ('proj:list',
                      'req:list',
                      'trace:list','trace:matrix','trace:coverage','trace:gaps',
                      'chg:list',
                      'soup:list','safety:read',
                      'baseline:list','baseline:compare',
                      'esign:read','esign:verify',
                      'risk:list','pr:list',
                      'report:dashboard','report:stats','report:export',
                      'regulation:read');

-- ============ 测试用户（密码统一 admin123 的 BCrypt hash） ============
-- BCrypt 加密 admin123 多次生成的等价哈希（强度 10）
-- 注：$2a$10$... 开头的合法 BCrypt 格式
DO $$
DECLARE
    v_pwd VARCHAR(200) := '$2b$10$3tCe7It.jXtlieZStLN/OuTpHamNIQvydNmriEaaYW3GOYXE/uvt2';
BEGIN
    -- 8 类用户（用户名 = 角色名小写）
    INSERT INTO sys_schema.t_user (username, password_hash, real_name, email, role, status)
    VALUES
    ('admin',      v_pwd, '系统管理员', 'admin@medrms.local',      'ADMIN',      'ACTIVE'),
    ('qa_mgr',     v_pwd, 'QA经理-张三', 'qa@medrms.local',        'QA_MGR',     'ACTIVE'),
    ('pm',         v_pwd, '项目经理-李四','pm@medrms.local',       'PM',         'ACTIVE'),
    ('re',         v_pwd, '需求工程师-王五','re@medrms.local',     'RE',         'ACTIVE'),
    ('reviewer',   v_pwd, '评审专家-赵六','reviewer@medrms.local', 'REVIEWER',   'ACTIVE'),
    ('risk_mgr',   v_pwd, '风险管理-钱七','risk@medrms.local',     'RISK_MGR',   'ACTIVE'),
    ('compliance', v_pwd, '合规人员-孙八','compliance@medrms.local','COMPLIANCE','ACTIVE'),
    ('viewer',     v_pwd, '只读用户-周九','viewer@medrms.local',   'VIEWER',     'ACTIVE')
    ON CONFLICT (username) DO NOTHING;
END $$;

-- 用户-角色关联（按用户名匹配）
INSERT INTO sys_schema.t_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_schema.t_user u
JOIN sys_schema.t_role r ON r.role_code = u.role
WHERE u.username IN ('admin','qa_mgr','pm','re','reviewer','risk_mgr','compliance','viewer')
ON CONFLICT DO NOTHING;
