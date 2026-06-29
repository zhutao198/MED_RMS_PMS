-- ============================================================================
-- 122. 业务字典枚举完善（v1.25 R26）
-- 清理重复 + 补全 11 类核心业务字典
-- ============================================================================

-- 清理重复（保留 id 最小的）
DELETE FROM sys_schema.t_dict_item a
USING sys_schema.t_dict_item b
WHERE a.id > b.id
  AND a.dict_type = b.dict_type
  AND a.item_code = b.item_code
  AND a.is_deleted = false
  AND b.is_deleted = false;

-- 补全字典（dict_type 不存在则插入，存在则跳过）
-- 1. 通用状态
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order)
SELECT 'common_status', 'ACTIVE',   '启用',   1
WHERE NOT EXISTS (SELECT 1 FROM sys_schema.t_dict_item WHERE dict_type='common_status' AND item_code='ACTIVE');
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order)
SELECT 'common_status', 'INACTIVE', '停用',   2
WHERE NOT EXISTS (SELECT 1 FROM sys_schema.t_dict_item WHERE dict_type='common_status' AND item_code='INACTIVE');

-- 2. 用户状态
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order) VALUES
('user_status', 'ACTIVE',  '正常',   1),
('user_status', 'LOCKED',  '已锁定', 2),
('user_status', 'INACTIVE','已停用', 3)
ON CONFLICT DO NOTHING;

-- 3. 需求状态
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order) VALUES
('req_status', 'DRAFT',     '草稿',   1),
('req_status', 'REVIEWING', '评审中', 2),
('req_status', 'APPROVED',  '已批准', 3),
('req_status', 'VERIFIED',  '已验证', 4),
('req_status', 'BASELINE',  '已基线', 5),
('req_status', 'REJECTED',  '已驳回', 6),
('req_status', 'DEPRECATED','已废弃', 7)
ON CONFLICT DO NOTHING;

-- 4. 需求层级（已存在但缺失 DRS 之后层级？检查 OK：URS/PRS/SRS/DRS 已有）
-- 不变

-- 5. 风险状态
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order) VALUES
('risk_status', 'OPEN',       '已识别',   1),
('risk_status', 'ANALYZING',  '分析中',   2),
('risk_status', 'MITIGATING', '缓解中',   3),
('risk_status', 'MITIGATED',  '已缓解',   4),
('risk_status', 'ACCEPTED',   '已接受',   5),
('risk_status', 'CLOSED',     '已关闭',   6)
ON CONFLICT DO NOTHING;

-- 6. 风险处理措施
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order) VALUES
('risk_action_status', 'OPEN',        '未开始',   1),
('risk_action_status', 'IN_PROGRESS', '进行中',   2),
('risk_action_status', 'COMPLETED',   '已完成',   3),
('risk_action_status', 'CANCELLED',   '已取消',   4)
ON CONFLICT DO NOTHING;

-- 7. 变更状态
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order) VALUES
('change_status', 'DRAFT',       '草稿',     1),
('change_status', 'SUBMITTED',   '已提交',   2),
('change_status', 'REVIEWING',   '评审中',   3),
('change_status', 'APPROVED',    '已批准',   4),
('change_status', 'REJECTED',    '已驳回',   5),
('change_status', 'IMPLEMENTING','执行中',   6),
('change_status', 'CLOSED',      '已关闭',   7)
ON CONFLICT DO NOTHING;

-- 8. 变更类型（已存在 MAJOR/NORMAL/DOCUMENT/EMERGENCY）
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order)
SELECT 'change_type', 'MINOR', '微小变更', 5
WHERE NOT EXISTS (SELECT 1 FROM sys_schema.t_dict_item WHERE dict_type='change_type' AND item_code='MINOR');

-- 9. 基线状态
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order) VALUES
('baseline_status', 'DRAFT',   '草稿',   1),
('baseline_status', 'LOCKING', '锁定中', 2),
('baseline_status', 'LOCKED',  '已锁定', 3),
('baseline_status', 'UNLOCKED','已解锁', 4)
ON CONFLICT DO NOTHING;

-- 10. DCP 门控状态
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order) VALUES
('gate_status', 'PENDING',       '待评审', 1),
('gate_status', 'GO',            '通过',   2),
('gate_status', 'CONDITIONAL_GO','有条件通过', 3),
('gate_status', 'NO_GO',         '不通过', 4)
ON CONFLICT DO NOTHING;

-- 11. DCP 级别
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order) VALUES
('gate_level', 'DCP1', 'DCP1 概念阶段',   1),
('gate_level', 'DCP2', 'DCP2 计划阶段',   2),
('gate_level', 'DCP3', 'DCP3 设计阶段',   3),
('gate_level', 'DCP4', 'DCP4 实现阶段',   4),
('gate_level', 'DCP5', 'DCP5 发布阶段',   5)
ON CONFLICT DO NOTHING;

-- 12. 评审状态
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order) VALUES
('review_status', 'PENDING',  '待评审', 1),
('review_status', 'APPROVED', '已通过', 2),
('review_status', 'REJECTED', '已驳回', 3)
ON CONFLICT DO NOTHING;

-- 13. 测试用例状态
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order) VALUES
('testcase_status', 'PASS',    '通过',       1),
('testcase_status', 'FAIL',    '失败',       2),
('testcase_status', 'BLOCKED', '阻塞',       3),
('testcase_status', 'SKIPPED', '跳过',       4),
('testcase_status', 'PENDING', '待执行',     5)
ON CONFLICT DO NOTHING;

-- 14. 测试用例优先级
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order) VALUES
('testcase_priority', 'P0', 'P0 最高', 1),
('testcase_priority', 'P1', 'P1 高',   2),
('testcase_priority', 'P2', 'P2 中',   3),
('testcase_priority', 'P3', 'P3 低',   4)
ON CONFLICT DO NOTHING;

-- 15. 追溯关系类型
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order) VALUES
('trace_relation', 'DERIVES_FROM',  '派生自', 1),
('trace_relation', 'REFINES',       '细化',   2),
('trace_relation', 'SATISFIES',     '满足',   3),
('trace_relation', 'VERIFIES',      '验证',   4),
('trace_relation', 'ALLOCATES',     '分配',   5),
('trace_relation', 'RELATED_TO',    '关联',   6)
ON CONFLICT DO NOTHING;

-- 16. 安全分类（IEC62304 软件安全等级）
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order) VALUES
('safety_class', 'A', 'Class A - 无伤',         1),
('safety_class', 'B', 'Class B - 非严重伤',     2),
('safety_class', 'C', 'Class C - 严重伤或死',   3)
ON CONFLICT DO NOTHING;

-- 17. 签名意图
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order) VALUES
('sign_meaning', 'REVIEW',  '评审', 1),
('sign_meaning', 'APPROVE', '批准', 2),
('sign_meaning', 'CONFIRM', '确认', 3),
('sign_meaning', 'REJECT',  '驳回', 4)
ON CONFLICT DO NOTHING;

-- 18. 紧急程度
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order) VALUES
('urgency', 'LOW',      '低', 1),
('urgency', 'MEDIUM',   '中', 2),
('urgency', 'HIGH',     '高', 3),
('urgency', 'CRITICAL', '紧急', 4)
ON CONFLICT DO NOTHING;

-- 19. 项目状态
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order) VALUES
('project_status', 'PLANNING',   '规划中', 1),
('project_status', 'IN_PROGRESS','进行中', 2),
('project_status', 'SUSPENDED',  '已暂停', 3),
('project_status', 'COMPLETED',  '已完成', 4),
('project_status', 'CANCELLED',  '已取消', 5)
ON CONFLICT DO NOTHING;

-- 20. 通知类型（已存在）
-- 不变
