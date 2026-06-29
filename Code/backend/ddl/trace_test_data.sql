-- ============================================================================
-- 追溯链路测试数据
-- 建立 URS → PRS → SRS → DRS → TC 完整追溯链
-- ============================================================================

-- 清理旧的追溯关系（只清理 depth > 0 的，避免破坏自引用）
DELETE FROM req_schema.t_requirement_ancestor WHERE depth > 0;
DELETE FROM trace_schema.t_requirement_test_case;

-- 建立追溯链: URS-0001 (id=27) -> PRS-0001 (id=32) -> SRS-0001 (id=35) -> DRS-0001 (id=38)
-- 1. URS-0001 -> PRS-0001 (depth=1)
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (27, 32, 1) ON CONFLICT DO NOTHING;

-- 2. PRS-0001 -> SRS-0001 (depth=1)
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (32, 35, 1) ON CONFLICT DO NOTHING;

-- 3. SRS-0001 -> DRS-0001 (depth=1)
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (35, 38, 1) ON CONFLICT DO NOTHING;

-- 完整链路的传递闭包: URS->SRS (depth=2), URS->DRS (depth=3)
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (27, 35, 2) ON CONFLICT DO NOTHING;
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (27, 38, 3) ON CONFLICT DO NOTHING;
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (32, 38, 2) ON CONFLICT DO NOTHING;

-- 4. URS-0002 (id=28) -> PRS-0002 (id=33) -> SRS-0002 (id=36) -> DRS-0002 (id=39)
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (28, 33, 1) ON CONFLICT DO NOTHING;
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (33, 36, 1) ON CONFLICT DO NOTHING;
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (36, 39, 1) ON CONFLICT DO NOTHING;
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (28, 36, 2) ON CONFLICT DO NOTHING;
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (28, 39, 3) ON CONFLICT DO NOTHING;
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (33, 39, 2) ON CONFLICT DO NOTHING;

-- 5. URS-0003 (id=29) -> PRS-0003 (id=34)
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (29, 34, 1) ON CONFLICT DO NOTHING;

-- 创建测试用例
INSERT INTO req_schema.t_test_case (test_case_no, test_case_name, test_type, test_method, requirement_id, requirement_no, status)
VALUES ('TC-ECG3-0001', '心电信号采集功能测试', 'SYSTEM', 'BLACK_BOX', 38, 'DRS-ECG3-0001', 'ACTIVE')
ON CONFLICT (test_case_no) DO UPDATE SET requirement_id = 38, requirement_no = 'DRS-ECG3-0001';

INSERT INTO req_schema.t_test_case (test_case_no, test_case_name, test_type, test_method, requirement_id, requirement_no, status)
VALUES ('TC-ECG3-0002', '心电波形显示功能测试', 'SYSTEM', 'BLACK_BOX', 39, 'DRS-ECG3-0002', 'ACTIVE')
ON CONFLICT (test_case_no) DO UPDATE SET requirement_id = 39, requirement_no = 'DRS-ECG3-0002';

-- 建立需求-测试用例追溯
INSERT INTO trace_schema.t_requirement_test_case (requirement_id, test_case_id, trace_type)
SELECT 38, tc.id, 'VERIFICATION'
FROM req_schema.t_test_case tc WHERE tc.test_case_no = 'TC-ECG3-0001'
ON CONFLICT DO NOTHING;

INSERT INTO trace_schema.t_requirement_test_case (requirement_id, test_case_id, trace_type)
SELECT 39, tc.id, 'VERIFICATION'
FROM req_schema.t_test_case tc WHERE tc.test_case_no = 'TC-ECG3-0002'
ON CONFLICT DO NOTHING;
