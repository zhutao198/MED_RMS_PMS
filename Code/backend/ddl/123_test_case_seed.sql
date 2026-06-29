-- ============================================================================
-- 123. 核心模块测试用例补充（v1.25 R26）
-- 在 TestCaseList 模块下补充 7 大模块的功能/性能/集成测试用例
-- ============================================================================

INSERT INTO req_schema.t_test_case
(test_case_no, test_case_name, test_type, test_method, requirement_id, requirement_no, version, status, safety_class, description, pre_condition, test_steps, expected_result)
VALUES
-- 1. 需求管理模块 (5)
('TC-REQ-001', '需求创建必填字段校验',         '功能测试', '等价类划分', 1, 'URS-008-001', 1, 'Approved', 'A',
 '验证创建需求时缺关键字段应被拒绝',
 '用户已登录并有 req:create 权限',
 '1) 留空 title 调用 POST /requirements; 2) 留空 requirementType; 3) 留空 projectId',
 '返回 400 SY0101，提示字段必填'),

('TC-REQ-002', '需求状态机转移-Draft到Reviewing',  '功能测试', '状态机',     1, 'URS-008-001', 1, 'Approved', 'A',
 'Draft 状态需求提交评审后应变为 Reviewing',
 '需求为 Draft 状态、当前用户为创建者或 PM',
 '1) 选中 Draft 需求; 2) 点击"提交评审"; 3) 输入评审人',
 '状态变为 Reviewing；触发通知给评审人；产生审计日志'),

('TC-REQ-003', '需求拆解-URS生成PRS',          '功能测试', '场景法',     2, 'URS-008-002', 1, 'Approved', 'A',
 'URS 拆解为 PRS 时建立双向追溯链接',
 '存在一个 Approved 状态的 URS',
 '1) 打开 URS 详情; 2) 点击"拆解"; 3) 填写 PRS 标题/描述; 4) 提交',
 '生成 PRS 记录；t_requirement_relation 增加 DERIVES_FROM 链接'),

('TC-REQ-004', '需求版本管理',                '功能测试', '回溯法',     1, 'URS-008-001', 1, 'InReview', 'B',
 '需求更新应保留历史版本',
 '需求为 Approved 状态',
 '1) 修改需求标题; 2) 保存; 3) 打开版本历史',
 't_requirement_version 增加一条 version_no=N+1 记录；新版本为 Draft'),

('TC-REQ-005', '需求导入Excel批量创建',         '集成测试', '端到端',     1, 'URS-008-001', 1, 'Draft',    'A',
 '通过 Excel 导入 100 条需求',
 '上传模板 .xlsx 文件',
 '1) 进入需求列表; 2) 点击"导入"; 3) 选择文件; 4) 提交',
 '返回成功条数；t_requirement 增加 100 条；失败条数显示在错误列表'),

-- 2. 追溯管理 (3)
('TC-TRC-001', '追溯矩阵渲染',                '功能测试', 'UI 验证',    1, 'URS-008-001', 1, 'Approved', 'A',
 '验证追溯矩阵正确显示 URS-PRS-SRS-DRS-TestCase 5 级关系',
 '存在完整 URS-PRS-SRS-DRS-TestCase 链',
 '1) 打开追溯矩阵; 2) 选择项目',
 '5 列展示；行数等于需求总数；链接可视化'),

('TC-TRC-002', '追溯覆盖率计算',              '算法测试', '边界值',     1, 'URS-008-001', 1, 'Approved', 'A',
 '验证覆盖率 = 已追溯需求数 / 总需求数',
 '存在 100 条需求，其中 80 条有下游追溯',
 '1) 打开追溯覆盖率; 2) 触发重算',
 '显示 80% 覆盖率；分模块显示（URS 100% / PRS 90% / SRS 80% / DRS 70%）'),

('TC-TRC-003', '追溯缺口检测',                '功能测试', '场景法',     1, 'URS-008-001', 1, 'Approved', 'A',
 '检测出没有测试用例的需求',
 '存在 5 条 SRS 无 TestCase 关联',
 '1) 打开追溯缺口; 2) 触发扫描',
 '列表展示 5 条 SRS；点击可跳转需求详情'),

-- 3. 变更管理 (3)
('TC-CHG-001', '变更创建与影响分析',           '功能测试', '场景法',     1, 'URS-008-001', 1, 'Approved', 'B',
 '创建变更请求时自动分析影响范围',
 '需求已 Approved 状态',
 '1) 打开变更创建; 2) 选择关联需求; 3) 描述变更; 4) 提交',
 '返回 impactAnalysis JSON：受影响需求 5 条 / 风险 2 条 / 测试用例 8 条'),

('TC-CHG-002', '变更审批-单人批准',           '功能测试', '状态机',     1, 'URS-008-001', 1, 'Approved', 'B',
 'PM 单人批准变更后状态变为 Approved',
 '变更处于 Reviewing 状态，当前用户为 PM',
 '1) 打开变更详情; 2) 点击"批准"; 3) 输入意见',
 '状态变为 Approved；记录审批历史'),

('TC-CHG-003', '变更执行-基线重建',            '集成测试', '端到端',     1, 'URS-008-001', 1, 'Approved', 'B',
 '变更执行后影响基线内需求',
 '变更已 Approved',
 '1) 打开变更详情; 2) 点击"执行"; 3) 选择目标基线',
 '基线内需求更新；版本号 +1；触发通知'),

-- 4. 合规管理 (3)
('TC-CMP-001', 'IEC62304 检查清单',            '功能测试', '清单验证',   NULL, NULL,         1, 'Approved', 'C',
 '验证软件安全等级 B 项目的检查项自动勾选',
 '项目安全分类 = Class B',
 '1) 打开 IEC62304 检查; 2) 选择项目',
 'Class B 必填项 24 项被标记 required；Class A 必填项 12 项被标记 optional'),

('TC-CMP-002', 'DHF 证据包导出',              '集成测试', '端到端',     NULL, NULL,         1, 'Approved', 'C',
 '导出 DHF 证据包 ZIP',
 '项目已锁定基线 ≥1 个',
 '1) 打开 DHF 证据包; 2) 选择基线; 3) 点击"导出"',
 '下载 ZIP 含：需求.pdf / 风险评估.xlsx / 测试报告.pdf / 评审记录.pdf / 签名记录.pdf'),

('TC-CMP-003', '问题报告创建与纠正',          '功能测试', '状态机',     NULL, NULL,         1, 'Approved', 'B',
 '问题报告从 Open 到 Closed 全流程',
 'QA 发现一个需求描述错误',
 '1) 创建问题报告; 2) 关联需求; 3) 触发纠正; 4) 验证; 5) 关闭',
 '问题状态：Open → InProgress → Resolved → Closed；产生纠正措施记录'),

-- 5. 风险管理 (3)
('TC-RISK-001', 'FMEA 计算 RPN',              '算法测试', '边界值',     NULL, NULL,         1, 'Approved', 'C',
 '验证 RPN = S × O × D',
 '存在 5 条风险数据',
 '1) 打开 FMEA 编辑; 2) 输入 S=8, O=5, D=4; 3) 保存',
 'RPN 自动计算为 160；矩阵定位 High 区域'),

('TC-RISK-002', '风险接受流程',                '功能测试', '权限验证',   NULL, NULL,         1, 'Approved', 'C',
 'QA_MGR 可接受风险，其他角色无权限',
 '风险处于 Mitigating 状态，RPN=200',
 '1) QA 调 /risk/register/{id}/accept; 2) Viewer 调同样接口',
 'QA 返回 200；Viewer 返回 403 SY0401'),

('TC-RISK-003', '风险控制措施跟踪',            '功能测试', '状态机',     NULL, NULL,         1, 'Approved', 'B',
 '风险控制措施从 Open 到 Completed',
 '风险已识别、措施已分配责任人',
 '1) 打开风险详情; 2) 添加控制措施; 3) 责任人更新状态',
 '措施状态：Open → InProgress → Completed；风险等级自动重新评估'),

-- 6. 电子签名 (2)
('TC-SIG-001', '签名意图创建与OTP',           '集成测试', '端到端',     NULL, NULL,         1, 'Approved', 'C',
 '签名意图创建后生成 6 位 OTP',
 '用户配置了签名密码和邮件',
 '1) 调用 POST /esign/intent; 2) 检查邮件',
 'intent_id 返回；6 位 OTP 发送至邮箱；10 分钟内有效'),

('TC-SIG-002', '签名完成-签名值计算',          '算法测试', '一致性',     NULL, NULL,         1, 'Approved', 'C',
 '签名值 = SHA256(entityType|entityId|entityHash|meaningCode|signerId|timestamp)',
 '准备完整签名请求',
 '1) 调用 POST /esign/sign; 2) 重新计算签名值',
 '签名值匹配；记录到 signature_record'),

-- 7. 基线管理 (2)
('TC-BSL-001', '基线锁定前置条件',             '功能测试', '场景法',     NULL, NULL,         1, 'Approved', 'C',
 '存在 Draft 需求时锁定应失败',
 '项目存在 Draft 状态需求',
 '1) 打开基线; 2) 点击"锁定"',
 '返回 409 条件不满足；提示"3 条 Draft 需求未处理"'),

('TC-BSL-002', '基线锁定-双人签名',            '集成测试', '端到端',     NULL, NULL,         1, 'Approved', 'C',
 'QA 第一签 + PM 第二签后基线 Locked',
 '基线所有需求 Approved',
 '1) QA 调用锁定; 2) QA 完成签名 1; 3) PM 完成签名 2',
 '基线状态：Draft → Locking → Locked；记录 2 条 signature_record')

ON CONFLICT DO NOTHING;
